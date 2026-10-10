/*
 * Copyright 2002-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.model.VisitStatus;
import org.springframework.samples.petclinic.repository.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentServiceImpl implements AppointmentService {

    static final LocalTime OPENING_TIME = LocalTime.of(9, 0);
    static final LocalTime CLOSING_TIME = LocalTime.of(17, 0);
    /** Appointments start and last in multiples of this many minutes. */
    static final int BOOKING_INTERVAL_MINUTES = 15;
    static final int MIN_DURATION_MINUTES = 15;
    static final int MAX_DURATION_MINUTES = 120;
    /** Length of the free slots reported by {@link #getSchedule}. */
    static final int AVAILABILITY_SLOT_MINUTES = 30;

    private final VisitRepository visitRepository;
    private final Clock clock;

    public AppointmentServiceImpl(VisitRepository visitRepository, Clock clock) {
        this.visitRepository = visitRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Visit bookAppointment(Vet vet, Pet pet, LocalDate date, LocalTime startTime, int durationMinutes, String description) {
        validateSlot(date, startTime, durationMinutes);
        LocalTime endTime = startTime.plusMinutes(durationMinutes);

        for (Visit booked : visitRepository.findByVetIdAndDate(vet.getId(), date)) {
            if (overlaps(booked, startTime, endTime)) {
                throw new AppointmentConflictException("Vet %d is already booked from %s to %s on %s"
                    .formatted(vet.getId(), booked.getStartTime(), booked.getEndTime(), date));
            }
        }
        for (Visit booked : visitRepository.findByPetId(pet.getId())) {
            if (date.equals(booked.getDate()) && overlaps(booked, startTime, endTime)) {
                throw new AppointmentConflictException("Pet %d already has an appointment from %s to %s on %s"
                    .formatted(pet.getId(), booked.getStartTime(), booked.getEndTime(), date));
            }
        }

        Visit visit = new Visit();
        visit.setPet(pet);
        visit.setVet(vet);
        visit.setDate(date);
        visit.setStartTime(startTime);
        visit.setDurationMinutes(durationMinutes);
        visit.setDescription(description);
        visit.setStatus(VisitStatus.SCHEDULED);
        visitRepository.save(visit);
        return visit;
    }

    @Override
    @Transactional(readOnly = true)
    public VetDaySchedule getSchedule(Vet vet, LocalDate date) {
        List<Visit> appointments = visitRepository.findByVetIdAndDate(vet.getId(), date).stream()
            .filter(Visit::isScheduledAppointment)
            .sorted(Comparator.comparing(Visit::getStartTime))
            .toList();

        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalTime> availableSlots = new ArrayList<>();
        for (LocalTime slot = OPENING_TIME;
             !slot.plusMinutes(AVAILABILITY_SLOT_MINUTES).isAfter(CLOSING_TIME);
             slot = slot.plusMinutes(AVAILABILITY_SLOT_MINUTES)) {
            LocalTime slotStart = slot;
            LocalTime slotEnd = slot.plusMinutes(AVAILABILITY_SLOT_MINUTES);
            boolean free = appointments.stream().noneMatch(booked -> overlaps(booked, slotStart, slotEnd));
            if (free && LocalDateTime.of(date, slotStart).isAfter(now)) {
                availableSlots.add(slotStart);
            }
        }
        return new VetDaySchedule(vet, date, appointments, availableSlots);
    }

    @Override
    @Transactional
    public Visit cancelAppointment(Visit visit) {
        if (visit.getVet() == null || visit.getStartTime() == null) {
            throw new InvalidAppointmentException("Visit %d is not a booked appointment".formatted(visit.getId()));
        }
        if (visit.getStatus() == VisitStatus.CANCELLED) {
            throw new AppointmentConflictException("Appointment %d is already cancelled".formatted(visit.getId()));
        }
        if (!LocalDateTime.of(visit.getDate(), visit.getStartTime()).isAfter(LocalDateTime.now(clock))) {
            throw new AppointmentConflictException("Appointment %d has already started".formatted(visit.getId()));
        }
        visit.setStatus(VisitStatus.CANCELLED);
        visitRepository.save(visit);
        return visit;
    }

    private void validateSlot(LocalDate date, LocalTime startTime, int durationMinutes) {
        if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES
            || durationMinutes % BOOKING_INTERVAL_MINUTES != 0) {
            throw new InvalidAppointmentException("Duration must be %d-%d minutes in steps of %d"
                .formatted(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES, BOOKING_INTERVAL_MINUTES));
        }
        if (startTime.getMinute() % BOOKING_INTERVAL_MINUTES != 0 || startTime.getSecond() != 0 || startTime.getNano() != 0) {
            throw new InvalidAppointmentException("Appointments start on a %d-minute boundary"
                .formatted(BOOKING_INTERVAL_MINUTES));
        }
        // Compare against the latest allowed start rather than the end time, so a late start
        // cannot wrap past midnight and look like it ends before closing.
        if (startTime.isBefore(OPENING_TIME) || startTime.isAfter(CLOSING_TIME.minusMinutes(durationMinutes))) {
            throw new InvalidAppointmentException("Appointments must fit between %s and %s"
                .formatted(OPENING_TIME, CLOSING_TIME));
        }
        if (!LocalDateTime.of(date, startTime).isAfter(LocalDateTime.now(clock))) {
            throw new InvalidAppointmentException("Appointments cannot be booked in the past");
        }
    }

    /**
     * Half-open interval overlap, so back-to-back appointments (one ends at 10:30, the next starts
     * at 10:30) do not conflict. Cancelled visits and visits without a time slot never overlap.
     */
    private static boolean overlaps(Visit booked, LocalTime start, LocalTime end) {
        return booked.isScheduledAppointment()
            && start.isBefore(booked.getEndTime())
            && booked.getStartTime().isBefore(end);
    }
}
