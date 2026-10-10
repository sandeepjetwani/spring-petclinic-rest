package org.springframework.samples.petclinic.service.appointmentService;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.model.VisitStatus;
import org.springframework.samples.petclinic.service.AppointmentConflictException;
import org.springframework.samples.petclinic.service.AppointmentService;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.samples.petclinic.service.VetDaySchedule;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Appointment scheduling against a real database. Subclasses run it with each persistence profile.
 * Bookings use a date far in the future so they are not affected by the real clock.
 */
abstract class AbstractAppointmentServiceTests {

    private static final LocalDate FUTURE_DAY = LocalDate.of(2099, 3, 2);

    @Autowired
    protected AppointmentService appointmentService;

    @Autowired
    protected ClinicService clinicService;

    @Test
    void shouldListSeededAppointmentsForVet() {
        // seed data: vet 2 has pet 1 at 10:00 (30 min) and pet 3 at 11:00 (45 min) on 2026-11-02
        Vet vet = this.clinicService.findVetById(2);

        VetDaySchedule schedule = this.appointmentService.getSchedule(vet, LocalDate.of(2026, 11, 2));

        assertThat(schedule.appointments()).extracting(Visit::getStartTime)
            .containsExactly(LocalTime.of(10, 0), LocalTime.of(11, 0));
        assertThat(schedule.appointments()).extracting(visit -> visit.getPet().getId())
            .containsExactly(1, 3);
        assertThat(schedule.appointments()).extracting(Visit::getDurationMinutes)
            .containsExactly(30, 45);
    }

    @Test
    void shouldNotListCancelledSeededAppointment() {
        // seed data: vet 3's only appointment on 2026-11-03 is cancelled
        Vet vet = this.clinicService.findVetById(3);

        assertThat(this.appointmentService.getSchedule(vet, LocalDate.of(2026, 11, 3)).appointments()).isEmpty();
    }

    @Test
    @Transactional
    void shouldBookAppointmentAndReadItBack() {
        Vet vet = this.clinicService.findVetById(1);
        Pet pet = this.clinicService.findPetById(1);

        Visit booked = this.appointmentService.bookAppointment(vet, pet, FUTURE_DAY, LocalTime.of(10, 0), 45, "annual checkup");
        assertThat(booked.getId()).isNotNull();
        clearCache();

        Visit found = this.clinicService.findVisitById(booked.getId());
        assertThat(found.getVet().getId()).isEqualTo(1);
        assertThat(found.getPet().getId()).isEqualTo(1);
        assertThat(found.getDate()).isEqualTo(FUTURE_DAY);
        assertThat(found.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(found.getDurationMinutes()).isEqualTo(45);
        assertThat(found.getStatus()).isEqualTo(VisitStatus.SCHEDULED);

        VetDaySchedule schedule = this.appointmentService.getSchedule(vet, FUTURE_DAY);
        assertThat(schedule.appointments()).extracting(Visit::getId).containsExactly(booked.getId());
        assertThat(schedule.availableSlots()).doesNotContain(LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertThat(schedule.availableSlots()).contains(LocalTime.of(9, 30), LocalTime.of(11, 0));
    }

    @Test
    @Transactional
    void shouldRejectOverlappingBooking() {
        Vet vet = this.clinicService.findVetById(1);
        this.appointmentService.bookAppointment(vet, this.clinicService.findPetById(1), FUTURE_DAY, LocalTime.of(14, 0), 60, "surgery");

        Pet otherPet = this.clinicService.findPetById(2);
        assertThatThrownBy(() -> this.appointmentService.bookAppointment(vet, otherPet, FUTURE_DAY, LocalTime.of(14, 30), 30, "checkup"))
            .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    @Transactional
    void shouldRejectSamePetWithAnotherVetAtTheSameTime() {
        Pet pet = this.clinicService.findPetById(1);
        this.appointmentService.bookAppointment(this.clinicService.findVetById(1), pet, FUTURE_DAY, LocalTime.of(9, 0), 30, "checkup");

        Vet otherVet = this.clinicService.findVetById(2);
        assertThatThrownBy(() -> this.appointmentService.bookAppointment(otherVet, pet, FUTURE_DAY, LocalTime.of(9, 15), 30, "x-ray"))
            .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    @Transactional
    void shouldCancelAppointmentAndFreeTheSlot() {
        Vet vet = this.clinicService.findVetById(1);
        Pet pet = this.clinicService.findPetById(1);
        Visit booked = this.appointmentService.bookAppointment(vet, pet, FUTURE_DAY, LocalTime.of(16, 0), 30, "checkup");

        this.appointmentService.cancelAppointment(this.clinicService.findVisitById(booked.getId()));
        clearCache();

        assertThat(this.clinicService.findVisitById(booked.getId()).getStatus()).isEqualTo(VisitStatus.CANCELLED);
        assertThat(this.appointmentService.getSchedule(vet, FUTURE_DAY).appointments()).isEmpty();
        Visit rebooked = this.appointmentService.bookAppointment(vet, this.clinicService.findPetById(2), FUTURE_DAY, LocalTime.of(16, 0), 30, "checkup");
        assertThat(rebooked.getId()).isNotEqualTo(booked.getId());
    }

    @Test
    void shouldLoadAppointmentFieldsWithOwnersPets() {
        // seed data: owner 1's pet 1 (Leo) has an appointment with vet 2 at 10:00
        Owner owner = this.clinicService.findOwnerById(1);

        Visit appointment = owner.getPet(1).getVisits().stream()
            .filter(visit -> visit.getVet() != null)
            .findFirst()
            .orElseThrow();
        assertThat(appointment.getVet().getId()).isEqualTo(2);
        assertThat(appointment.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(appointment.getStatus()).isEqualTo(VisitStatus.SCHEDULED);
    }

    void clearCache() {}
}
