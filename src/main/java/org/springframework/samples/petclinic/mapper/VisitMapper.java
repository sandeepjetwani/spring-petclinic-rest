package org.springframework.samples.petclinic.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.rest.dto.VisitDto;
import org.springframework.samples.petclinic.rest.dto.VisitFieldsDto;

import java.util.Collection;

/**
 * Map Visit & VisitDto using mapstruct
 */
@Mapper(uses = PetMapper.class)
public interface VisitMapper {
    // Appointment fields are read-only here; appointments are booked through AppointmentService.
    @Mapping(source = "petId", target = "pet.id")
    @Mapping(target = "vet", ignore = true)
    @Mapping(target = "startTime", ignore = true)
    @Mapping(target = "durationMinutes", ignore = true)
    @Mapping(target = "status", ignore = true)
    Visit toVisit(VisitDto visitDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pet", ignore = true)
    @Mapping(target = "vet", ignore = true)
    @Mapping(target = "startTime", ignore = true)
    @Mapping(target = "durationMinutes", ignore = true)
    @Mapping(target = "status", ignore = true)
    Visit toVisit(VisitFieldsDto visitFieldsDto);

    @Mapping(source = "pet.id", target = "petId")
    @Mapping(source = "vet.id", target = "vetId")
    @Mapping(source = "startTime", target = "startTime", dateFormat = "HH:mm")
    VisitDto toVisitDto(Visit visit);

    Collection<VisitDto> toVisitsDto(Collection<Visit> visits);

}
