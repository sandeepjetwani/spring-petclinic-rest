package org.springframework.samples.petclinic.rest.controller.v2;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.mapper.OwnerMapper;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.repository.OwnerSearchCriteria;
import org.springframework.samples.petclinic.rest.api.OwnerV2Api;
import org.springframework.samples.petclinic.rest.dto.OwnerPageDto;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Set;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("/api")
public class OwnerRestControllerV2 implements OwnerV2Api {

    /** The values of the {@code sort} parameter in openapi.yml; the generated API does not enforce them. */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "lastName", "city");

    private final ClinicService clinicService;
    private final OwnerMapper ownerMapper;

    public OwnerRestControllerV2(ClinicService clinicService, OwnerMapper ownerMapper) {
        this.clinicService = clinicService;
        this.ownerMapper = ownerMapper;
    }

    @Override
    public ResponseEntity<OwnerPageDto> listOwnersPage(String lastName, String city, String sort, String direction,
                                                       Integer page, Integer size) {
        String sortProperty = sort == null ? "id" : sort;
        if (!SORTABLE_PROPERTIES.contains(sortProperty)
            || (direction != null && !direction.equals("asc") && !direction.equals("desc"))) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        Sort.Direction sortDirection = "desc".equals(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Page<Owner> owners = this.clinicService.searchOwners(
            new OwnerSearchCriteria(lastName, city),
            PageRequest.of(pageNumber, pageSize, Sort.by(sortDirection, sortProperty)));
        return new ResponseEntity<>(ownerMapper.toOwnerPageDto(owners), HttpStatus.OK);
    }
}
