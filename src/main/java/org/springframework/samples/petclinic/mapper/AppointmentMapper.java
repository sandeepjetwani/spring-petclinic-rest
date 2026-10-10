package org.springframework.samples.petclinic.mapper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.samples.petclinic.rest.dto.VetScheduleDto;
import org.springframework.samples.petclinic.service.VetDaySchedule;

/**
 * Map a vet's day schedule to its REST representation using mapstruct
 */
@Mapper(uses = VisitMapper.class)
public interface AppointmentMapper {

    DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Mapping(source = "vet.id", target = "vetId")
    VetScheduleDto toVetScheduleDto(VetDaySchedule schedule);

    default String formatTime(LocalTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }
}
