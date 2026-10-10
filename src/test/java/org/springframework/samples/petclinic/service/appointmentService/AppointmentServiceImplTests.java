package org.springframework.samples.petclinic.service.appointmentService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.model.VisitStatus;
import org.springframework.samples.petclinic.repository.VisitRepository;
import org.springframework.samples.petclinic.service.AppointmentConflictException;
import org.springframework.samples.petclinic.service.AppointmentServiceImpl;
import org.springframework.samples.petclinic.service.InvalidAppointmentException;
import org.springframework.samples.petclinic.service.VetDaySchedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Booking rules of {@link AppointmentServiceImpl}, with a fixed clock: "now" is 2030-06-03 10:05.
 */
class AppointmentServiceImplTests {

    private static final LocalDate TODAY = LocalDate.of(2030, 6, 3);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    private VisitRepository visitRepository;
    private AppointmentServiceImpl appointmentService;
    private Vet vet;
    private Pet pet;

    @BeforeEach
    void setUp() {
        visitRepository = mock(VisitRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2030-06-03T10:05:00Z"), ZoneOffset.UTC);
        appointmentService = new AppointmentServiceImpl(visitRepository, clock);
        vet = new Vet();
        vet.setId(2);
        pet = new Pet();
        pet.setId(1);
        given(visitRepository.findByVetIdAndDate(any(Integer.class), any())).willReturn(List.of());
        given(visitRepository.findByPetId(any())).willReturn(List.of());
    }

    @Test
    void shouldBookFreeSlot() {
        Visit visit = appointmentService.bookAppointment(vet, pet, TOMORROW, LocalTime.of(10, 30), 45, "annual checkup");

        ArgumentCaptor<Visit> saved = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(visit);
        assertThat(visit.getVet()).isSameAs(vet);
        assertThat(visit.getPet()).isSameAs(pet);
        assertThat(visit.getDate()).isEqualTo(TOMORROW);
        assertThat(visit.getStartTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(visit.getEndTime()).isEqualTo(LocalTime.of(11, 15));
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.SCHEDULED);
    }

    @ParameterizedTest(name = "{0} for {1} min overlaps 10:00-10:30")
    @CsvSource({
        "10:00, 30",   // same slot
        "09:45, 30",   // overlaps the start
        "10:15, 30",   // overlaps the end
        "09:30, 90",   // contains it
        "10:15, 15"    // inside it
    })
    void shouldRejectOverlapWithVetsAppointment(String start, int duration) {
        given(visitRepository.findByVetIdAndDate(2, TOMORROW)).willReturn(List.of(appointment(5, TOMORROW, "10:00", 30)));

        assertThatThrownBy(() -> appointmentService.bookAppointment(vet, pet, TOMORROW, LocalTime.parse(start), duration, "checkup"))
            .isInstanceOf(AppointmentConflictException.class)
            .hasMessageContaining("Vet 2");
        verify(visitRepository, never()).save(any());
    }

    @ParameterizedTest(name = "{0} for {1} min is back-to-back with 10:00-10:30")
    @CsvSource({"09:30, 30", "10:30, 30"})
    void shouldAllowBackToBackAppointments(String start, int duration) {
        given(visitRepository.findByVetIdAndDate(2, TOMORROW)).willReturn(List.of(appointment(5, TOMORROW, "10:00", 30)));

        appointmentService.bookAppointment(vet, pet, TOMORROW, LocalTime.parse(start), duration, "checkup");

        verify(visitRepository).save(any());
    }

    @Test
    void shouldIgnoreCancelledAppointmentsAndVisitsWithoutTimeSlot() {
        Visit cancelled = appointment(5, TOMORROW, "10:00", 30);
        cancelled.setStatus(VisitStatus.CANCELLED);
        Visit walkIn = new Visit();
        walkIn.setDate(TOMORROW);
        given(visitRepository.findByVetIdAndDate(2, TOMORROW)).willReturn(List.of(cancelled, walkIn));

        appointmentService.bookAppointment(vet, pet, TOMORROW, LocalTime.of(10, 0), 30, "checkup");

        verify(visitRepository).save(any());
    }

    @Test
    void shouldRejectPetAlreadyBookedWithAnotherVet() {
        Visit withOtherVet = appointment(6, TOMORROW, "10:00", 60);
        given(visitRepository.findByPetId(1)).willReturn(List.of(withOtherVet));

        assertThatThrownBy(() -> appointmentService.bookAppointment(vet, pet, TOMORROW, LocalTime.of(10, 30), 30, "checkup"))
            .isInstanceOf(AppointmentConflictException.class)
            .hasMessageContaining("Pet 1");
    }

    @Test
    void shouldAllowPetAppointmentOnAnotherDay() {
        given(visitRepository.findByPetId(1)).willReturn(List.of(appointment(6, TOMORROW.plusDays(1), "10:00", 60)));

        appointmentService.bookAppointment(vet, pet, TOMORROW, LocalTime.of(10, 0), 30, "checkup");

        verify(visitRepository).save(any());
    }

    @ParameterizedTest(name = "{0} for {1} min is outside opening hours")
    @CsvSource({
        "08:45, 30",
        "16:45, 30",
        "16:00, 120",
        "23:30, 60"    // would wrap past midnight
    })
    void shouldRejectOutsideOpeningHours(String start, int duration) {
        assertThatThrownBy(() -> appointmentService.bookAppointment(vet, pet, TOMORROW, LocalTime.parse(start), duration, "checkup"))
            .isInstanceOf(InvalidAppointmentException.class)
            .hasMessageContaining("09:00");
    }

    @Test
    void shouldAcceptAppointmentEndingExactlyAtClosing() {
        appointmentService.bookAppointment(vet, pet, TOMORROW, LocalTime.of(15, 0), 120, "surgery");

        verify(visitRepository).save(any());
    }

    @ParameterizedTest(name = "{0} min is not a valid duration")
    @CsvSource({"0", "10", "20", "135"})
    void shouldRejectInvalidDuration(int duration) {
        assertThatThrownBy(() -> appointmentService.bookAppointment(vet, pet, TOMORROW, LocalTime.of(10, 0), duration, "checkup"))
            .isInstanceOf(InvalidAppointmentException.class)
            .hasMessageContaining("Duration");
    }

    @Test
    void shouldRejectStartNotOnBookingBoundary() {
        assertThatThrownBy(() -> appointmentService.bookAppointment(vet, pet, TOMORROW, LocalTime.of(10, 10), 30, "checkup"))
            .isInstanceOf(InvalidAppointmentException.class)
            .hasMessageContaining("15-minute");
    }

    @ParameterizedTest(name = "{0} today is not in the future")
    @CsvSource({"09:00", "10:00"})
    void shouldRejectBookingInThePast(String start) {
        assertThatThrownBy(() -> appointmentService.bookAppointment(vet, pet, TODAY, LocalTime.parse(start), 30, "checkup"))
            .isInstanceOf(InvalidAppointmentException.class)
            .hasMessageContaining("past");
    }

    @Test
    void shouldBookLaterToday() {
        appointmentService.bookAppointment(vet, pet, TODAY, LocalTime.of(10, 15), 30, "checkup");

        verify(visitRepository).save(any());
    }

    @Test
    void shouldListScheduledAppointmentsAndFreeSlots() {
        Visit late = appointment(7, TOMORROW, "14:00", 60);
        Visit early = appointment(5, TOMORROW, "09:15", 30);
        Visit cancelled = appointment(6, TOMORROW, "11:00", 30);
        cancelled.setStatus(VisitStatus.CANCELLED);
        given(visitRepository.findByVetIdAndDate(2, TOMORROW)).willReturn(List.of(late, cancelled, early));

        VetDaySchedule schedule = appointmentService.getSchedule(vet, TOMORROW);

        assertThat(schedule.appointments()).containsExactly(early, late);
        // 09:00 and 09:30 overlap 09:15-09:45; 14:00 and 14:30 overlap 14:00-15:00; the cancelled 11:00 is free
        assertThat(schedule.availableSlots()).containsExactly(
            LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30),
            LocalTime.of(12, 0), LocalTime.of(12, 30), LocalTime.of(13, 0), LocalTime.of(13, 30),
            LocalTime.of(15, 0), LocalTime.of(15, 30), LocalTime.of(16, 0), LocalTime.of(16, 30));
    }

    @Test
    void shouldOnlyOfferFutureSlotsToday() {
        VetDaySchedule schedule = appointmentService.getSchedule(vet, TODAY);

        assertThat(schedule.availableSlots()).first().isEqualTo(LocalTime.of(10, 30));
        assertThat(schedule.availableSlots()).last().isEqualTo(LocalTime.of(16, 30));
    }

    @Test
    void shouldCancelUpcomingAppointment() {
        Visit visit = appointment(5, TOMORROW, "10:00", 30);

        Visit cancelled = appointmentService.cancelAppointment(visit);

        assertThat(cancelled.getStatus()).isEqualTo(VisitStatus.CANCELLED);
        verify(visitRepository).save(visit);
    }

    @Test
    void shouldNotCancelTwice() {
        Visit visit = appointment(5, TOMORROW, "10:00", 30);
        visit.setStatus(VisitStatus.CANCELLED);

        assertThatThrownBy(() -> appointmentService.cancelAppointment(visit))
            .isInstanceOf(AppointmentConflictException.class)
            .hasMessageContaining("already cancelled");
        verify(visitRepository, never()).save(any());
    }

    @Test
    void shouldNotCancelAppointmentThatHasStarted() {
        Visit visit = appointment(5, TODAY, "10:00", 30);

        assertThatThrownBy(() -> appointmentService.cancelAppointment(visit))
            .isInstanceOf(AppointmentConflictException.class)
            .hasMessageContaining("already started");
    }

    @Test
    void shouldNotCancelVisitWithoutAppointment() {
        Visit walkIn = new Visit();
        walkIn.setId(1);
        walkIn.setDate(TOMORROW);

        assertThatThrownBy(() -> appointmentService.cancelAppointment(walkIn))
            .isInstanceOf(InvalidAppointmentException.class);
    }

    private Visit appointment(int id, LocalDate date, String start, int duration) {
        Visit visit = new Visit();
        visit.setId(id);
        visit.setVet(vet);
        visit.setPet(pet);
        visit.setDate(date);
        visit.setStartTime(LocalTime.parse(start));
        visit.setDurationMinutes(duration);
        visit.setDescription("booked");
        return visit;
    }
}
