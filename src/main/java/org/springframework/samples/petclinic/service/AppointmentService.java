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

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.model.Visit;

/**
 * Books, lists and cancels vet appointments.
 */
public interface AppointmentService {

    /**
     * Books {@code pet} with {@code vet} for the given slot.
     *
     * @throws InvalidAppointmentException if the slot is outside opening hours, not aligned to the
     *                                     booking interval, has an unsupported duration, or is in the past
     * @throws AppointmentConflictException if the vet or the pet already has an overlapping appointment
     */
    Visit bookAppointment(Vet vet, Pet pet, LocalDate date, LocalTime startTime, int durationMinutes, String description);

    /**
     * The vet's scheduled (not cancelled) appointments on {@code date} and the remaining free slots.
     */
    VetDaySchedule getSchedule(Vet vet, LocalDate date);

    /**
     * Cancels a booked appointment that has not started yet.
     *
     * @throws InvalidAppointmentException if the visit is not a booked appointment
     * @throws AppointmentConflictException if it is already cancelled or has already started
     */
    Visit cancelAppointment(Visit visit);
}
