package org.springframework.samples.petclinic.rest.controller.v1;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.mapper.AppointmentMapper;
import org.springframework.samples.petclinic.mapper.VisitMapper;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.rest.api.AppointmentsApi;
import org.springframework.samples.petclinic.rest.dto.AppointmentFieldsDto;
import org.springframework.samples.petclinic.rest.dto.VetScheduleDto;
import org.springframework.samples.petclinic.rest.dto.VisitDto;
import org.springframework.samples.petclinic.service.AppointmentService;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Books, lists and cancels vet appointments.
 */
@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api")
public class AppointmentRestControllerV1 implements AppointmentsApi {

    /** Matches the default declared for durationMinutes in openapi.yml. */
    private static final int DEFAULT_DURATION_MINUTES = 30;

    private final ClinicService clinicService;

    private final AppointmentService appointmentService;

    private final VisitMapper visitMapper;

    private final AppointmentMapper appointmentMapper;

    public AppointmentRestControllerV1(ClinicService clinicService,
                                       AppointmentService appointmentService,
                                       VisitMapper visitMapper,
                                       AppointmentMapper appointmentMapper) {
        this.clinicService = clinicService;
        this.appointmentService = appointmentService;
        this.visitMapper = visitMapper;
        this.appointmentMapper = appointmentMapper;
    }

    @PreAuthorize("hasAnyRole(@roles.OWNER_ADMIN, @roles.VET_ADMIN)")
    @Override
    public ResponseEntity<VisitDto> bookAppointment(Integer vetId, AppointmentFieldsDto appointmentFieldsDto) {
        Vet vet = this.clinicService.findVetById(vetId);
        Pet pet = this.clinicService.findPetById(appointmentFieldsDto.getPetId());
        if (vet == null || pet == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Visit visit = this.appointmentService.bookAppointment(
            vet,
            pet,
            appointmentFieldsDto.getDate(),
            LocalTime.parse(appointmentFieldsDto.getStartTime()),
            Objects.requireNonNullElse(appointmentFieldsDto.getDurationMinutes(), DEFAULT_DURATION_MINUTES),
            appointmentFieldsDto.getDescription());
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(UriComponentsBuilder.newInstance().path("/api/visits/{id}").buildAndExpand(visit.getId()).toUri());
        return new ResponseEntity<>(visitMapper.toVisitDto(visit), headers, HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole(@roles.OWNER_ADMIN, @roles.VET_ADMIN)")
    @Override
    public ResponseEntity<VetScheduleDto> getVetSchedule(Integer vetId, LocalDate date) {
        Vet vet = this.clinicService.findVetById(vetId);
        if (vet == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(appointmentMapper.toVetScheduleDto(this.appointmentService.getSchedule(vet, date)), HttpStatus.OK);
    }

    @PreAuthorize("hasAnyRole(@roles.OWNER_ADMIN, @roles.VET_ADMIN)")
    @Override
    public ResponseEntity<VisitDto> cancelAppointment(Integer vetId, Integer visitId) {
        Visit visit = this.clinicService.findVisitById(visitId);
        if (visit == null || visit.getVet() == null || !visit.getVet().getId().equals(vetId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(visitMapper.toVisitDto(this.appointmentService.cancelAppointment(visit)), HttpStatus.OK);
    }
}
