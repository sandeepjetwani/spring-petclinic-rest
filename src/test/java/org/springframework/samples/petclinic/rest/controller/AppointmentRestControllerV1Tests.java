package org.springframework.samples.petclinic.rest.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.model.VisitStatus;
import org.springframework.samples.petclinic.rest.advice.ExceptionControllerAdvice;
import org.springframework.samples.petclinic.rest.controller.v1.AppointmentRestControllerV1;
import org.springframework.samples.petclinic.service.AppointmentConflictException;
import org.springframework.samples.petclinic.service.AppointmentService;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.samples.petclinic.service.InvalidAppointmentException;
import org.springframework.samples.petclinic.service.VetDaySchedule;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ContextConfiguration(classes = ApplicationTestConfig.class)
@WebAppConfiguration
class AppointmentRestControllerV1Tests {

    private static final LocalDate DAY = LocalDate.of(2030, 6, 4);

    @Autowired
    private AppointmentRestControllerV1 appointmentRestControllerV1;

    @MockitoBean
    private ClinicService clinicService;

    @MockitoBean
    private AppointmentService appointmentService;

    private MockMvc mockMvc;

    private Vet vet;

    private Pet pet;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(appointmentRestControllerV1)
            .setControllerAdvice(new ExceptionControllerAdvice())
            .build();
        vet = new Vet();
        vet.setId(2);
        vet.setFirstName("Helen");
        vet.setLastName("Leary");
        pet = new Pet();
        pet.setId(1);
        pet.setName("Leo");
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testBookAppointmentSuccess() throws Exception {
        given(this.clinicService.findVetById(2)).willReturn(vet);
        given(this.clinicService.findPetById(1)).willReturn(pet);
        given(this.appointmentService.bookAppointment(vet, pet, DAY, LocalTime.of(10, 30), 45, "annual checkup"))
            .willReturn(appointment(10, "10:30", 45));

        this.mockMvc.perform(post("/api/vets/2/appointments")
                .content("{\"petId\":1,\"date\":\"2030-06-04\",\"startTime\":\"10:30\",\"durationMinutes\":45,\"description\":\"annual checkup\"}")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/visits/10"))
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.vetId").value(2))
            .andExpect(jsonPath("$.petId").value(1))
            .andExpect(jsonPath("$.startTime").value("10:30"))
            .andExpect(jsonPath("$.durationMinutes").value(45))
            .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testBookAppointmentUsesDefaultDuration() throws Exception {
        given(this.clinicService.findVetById(2)).willReturn(vet);
        given(this.clinicService.findPetById(1)).willReturn(pet);
        given(this.appointmentService.bookAppointment(vet, pet, DAY, LocalTime.of(9, 0), 30, "checkup"))
            .willReturn(appointment(11, "09:00", 30));

        this.mockMvc.perform(post("/api/vets/2/appointments")
                .content("{\"petId\":1,\"date\":\"2030-06-04\",\"startTime\":\"09:00\",\"description\":\"checkup\"}")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.durationMinutes").value(30));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testBookAppointmentUnknownVet() throws Exception {
        given(this.clinicService.findPetById(1)).willReturn(pet);

        this.mockMvc.perform(post("/api/vets/99/appointments")
                .content("{\"petId\":1,\"date\":\"2030-06-04\",\"startTime\":\"10:30\",\"description\":\"checkup\"}")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
        verify(this.appointmentService, never()).bookAppointment(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testBookAppointmentUnknownPet() throws Exception {
        given(this.clinicService.findVetById(2)).willReturn(vet);

        this.mockMvc.perform(post("/api/vets/2/appointments")
                .content("{\"petId\":99,\"date\":\"2030-06-04\",\"startTime\":\"10:30\",\"description\":\"checkup\"}")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
        verify(this.appointmentService, never()).bookAppointment(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testBookAppointmentConflict() throws Exception {
        given(this.clinicService.findVetById(2)).willReturn(vet);
        given(this.clinicService.findPetById(1)).willReturn(pet);
        given(this.appointmentService.bookAppointment(any(), any(), any(), any(), anyInt(), any()))
            .willThrow(new AppointmentConflictException("Vet 2 is already booked from 10:00 to 10:30 on 2030-06-04"));

        this.mockMvc.perform(post("/api/vets/2/appointments")
                .content("{\"petId\":1,\"date\":\"2030-06-04\",\"startTime\":\"10:15\",\"description\":\"checkup\"}")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Vet 2 is already booked from 10:00 to 10:30 on 2030-06-04"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testBookAppointmentBreaksRule() throws Exception {
        given(this.clinicService.findVetById(2)).willReturn(vet);
        given(this.clinicService.findPetById(1)).willReturn(pet);
        given(this.appointmentService.bookAppointment(any(), any(), any(), any(), anyInt(), any()))
            .willThrow(new InvalidAppointmentException("Appointments must fit between 09:00 and 17:00"));

        this.mockMvc.perform(post("/api/vets/2/appointments")
                .content("{\"petId\":1,\"date\":\"2030-06-04\",\"startTime\":\"18:00\",\"description\":\"checkup\"}")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Appointments must fit between 09:00 and 17:00"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testBookAppointmentRejectsMalformedRequest() throws Exception {
        String[] invalid = {
            "{\"petId\":1,\"date\":\"2030-06-04\",\"startTime\":\"9:5\",\"description\":\"checkup\"}",
            "{\"petId\":1,\"date\":\"2030-06-04\",\"startTime\":\"10:30\",\"durationMinutes\":180,\"description\":\"checkup\"}",
            "{\"petId\":1,\"date\":\"2030-06-04\",\"startTime\":\"10:30\"}"
        };
        for (String body : invalid) {
            this.mockMvc.perform(post("/api/vets/2/appointments").content(body).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
        verify(this.appointmentService, never()).bookAppointment(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    @WithMockUser(roles = "VET_ADMIN")
    void testGetVetSchedule() throws Exception {
        given(this.clinicService.findVetById(2)).willReturn(vet);
        given(this.appointmentService.getSchedule(vet, DAY)).willReturn(new VetDaySchedule(vet, DAY,
            List.of(appointment(10, "10:00", 30)),
            List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 30))));

        this.mockMvc.perform(get("/api/vets/2/schedule").param("date", "2030-06-04").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vetId").value(2))
            .andExpect(jsonPath("$.date").value("2030-06-04"))
            .andExpect(jsonPath("$.appointments[0].id").value(10))
            .andExpect(jsonPath("$.appointments[0].startTime").value("10:00"))
            .andExpect(jsonPath("$.availableSlots[0]").value("09:00"))
            .andExpect(jsonPath("$.availableSlots[2]").value("10:30"));
    }

    @Test
    @WithMockUser(roles = "VET_ADMIN")
    void testGetVetScheduleUnknownVet() throws Exception {
        this.mockMvc.perform(get("/api/vets/99/schedule").param("date", "2030-06-04"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "VET_ADMIN")
    void testGetVetScheduleRequiresDate() throws Exception {
        given(this.clinicService.findVetById(2)).willReturn(vet);

        this.mockMvc.perform(get("/api/vets/2/schedule"))
            .andExpect(status().isBadRequest());
        this.mockMvc.perform(get("/api/vets/2/schedule").param("date", "next-monday"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testCancelAppointment() throws Exception {
        Visit booked = appointment(10, "10:00", 30);
        Visit cancelled = appointment(10, "10:00", 30);
        cancelled.setStatus(VisitStatus.CANCELLED);
        given(this.clinicService.findVisitById(10)).willReturn(booked);
        given(this.appointmentService.cancelAppointment(booked)).willReturn(cancelled);

        this.mockMvc.perform(post("/api/vets/2/appointments/10/cancel").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testCancelAppointmentOfAnotherVet() throws Exception {
        given(this.clinicService.findVisitById(10)).willReturn(appointment(10, "10:00", 30));

        this.mockMvc.perform(post("/api/vets/3/appointments/10/cancel"))
            .andExpect(status().isNotFound());
        verify(this.appointmentService, never()).cancelAppointment(any());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testCancelVisitWithoutAppointment() throws Exception {
        Visit walkIn = new Visit();
        walkIn.setId(1);
        walkIn.setPet(pet);
        given(this.clinicService.findVisitById(1)).willReturn(walkIn);

        this.mockMvc.perform(post("/api/vets/2/appointments/1/cancel"))
            .andExpect(status().isNotFound());
        verify(this.appointmentService, never()).cancelAppointment(any());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testCancelAppointmentTwice() throws Exception {
        Visit visit = appointment(10, "10:00", 30);
        given(this.clinicService.findVisitById(10)).willReturn(visit);
        given(this.appointmentService.cancelAppointment(visit))
            .willThrow(new AppointmentConflictException("Appointment 10 is already cancelled"));

        this.mockMvc.perform(post("/api/vets/2/appointments/10/cancel"))
            .andExpect(status().isConflict());
    }

    private Visit appointment(int id, String start, int duration) {
        Visit visit = new Visit();
        visit.setId(id);
        visit.setVet(vet);
        visit.setPet(pet);
        visit.setDate(DAY);
        visit.setStartTime(LocalTime.parse(start));
        visit.setDurationMinutes(duration);
        visit.setDescription("annual checkup");
        return visit;
    }
}
