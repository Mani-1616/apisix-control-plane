package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateEnvironmentRequest;
import com.apisix.controlplane.entity.Environment;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.EnvironmentRepository;
import com.apisix.controlplane.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final OrganizationRepository organizationRepository;

    public Environment createEnvironment(String orgId, CreateEnvironmentRequest request) {
        log.info("Creating environment '{}' for organization: {}", request.getName(), orgId);
        
        // Validate organization exists
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization not found with ID: " + orgId);
        }

        // Check if environment with same name already exists in this org
        if (environmentRepository.existsByOrgIdAndName(orgId, request.getName())) {
            throw new BusinessException("Environment with name '" + request.getName() + "' already exists in this organization");
        }

        Environment environment = Environment.builder()
                .orgId(orgId)
                .name(request.getName())
                .description(request.getDescription())
                .apisixAdminUrl(request.getApisixAdminUrl())
                .active(request.isActive())
                .build();

        Environment saved = environmentRepository.save(environment);
        log.info("Environment created with ID: {}", saved.getId());
        return saved;
    }

    public List<Environment> getEnvironmentsByOrg(String orgId) {
        return environmentRepository.findByOrgId(orgId);
    }

    public Environment getEnvironmentById(String id) {
        return environmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with ID: " + id));
    }

    public void deleteEnvironment(String id) {
        if (!environmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Environment not found with ID: " + id);
        }
        environmentRepository.deleteById(id);
        log.info("Environment deleted with ID: {}", id);
    }
}

