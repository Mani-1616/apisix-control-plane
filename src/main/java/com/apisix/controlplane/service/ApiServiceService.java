package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateServiceRequest;
import com.apisix.controlplane.entity.Service;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.ApiServiceRepository;
import com.apisix.controlplane.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class ApiServiceService {

    private final ApiServiceRepository apiServiceRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public Service createService(String orgId, CreateServiceRequest request) {
        log.info("Creating service '{}' for organization: {}", request.getName(), orgId);

        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization not found with ID: " + orgId);
        }

        if (apiServiceRepository.existsByOrgIdAndName(orgId, request.getName())) {
            throw new BusinessException("Service with name '" + request.getName() + "' already exists in this organization");
        }

        Service service = Service.builder()
                .orgId(orgId)
                .name(request.getName())
                .displayName(request.getDisplayName())
                .build();

        Service saved = apiServiceRepository.save(service);
        log.info("Service created with ID: {}", saved.getId());
        return saved;
    }

    public Service getServiceById(String serviceId) {
        return apiServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with ID: " + serviceId));
    }

    public List<Service> getServicesByOrg(String orgId) {
        return apiServiceRepository.findByOrgId(orgId);
    }

    public Page<Service> getServicesByOrg(String orgId, Pageable pageable) {
        return apiServiceRepository.findByOrgId(orgId, pageable);
    }

    @Transactional
    public void deleteService(String serviceId) {
        Service service = getServiceById(serviceId);
        apiServiceRepository.delete(service);
        log.info("Service deleted: {}", serviceId);
    }
}
