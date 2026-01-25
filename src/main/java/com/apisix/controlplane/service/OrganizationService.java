package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateOrgRequest;
import com.apisix.controlplane.entity.Organization;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public Organization createOrganization(CreateOrgRequest request) {
        log.info("Creating organization: {}", request.getName());
        
        if (organizationRepository.existsByName(request.getName())) {
            throw new BusinessException("Organization with name '" + request.getName() + "' already exists");
        }

        Organization org = Organization.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        Organization saved = organizationRepository.save(org);
        log.info("Organization created with ID: {}", saved.getId());
        return saved;
    }

    public List<Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }

    public Organization getOrganizationById(String id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with ID: " + id));
    }

    public void deleteOrganization(String id) {
        if (!organizationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Organization not found with ID: " + id);
        }
        organizationRepository.deleteById(id);
        log.info("Organization deleted with ID: {}", id);
    }
}

