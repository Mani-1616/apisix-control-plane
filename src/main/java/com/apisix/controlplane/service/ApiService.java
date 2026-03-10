package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateApiRequest;
import com.apisix.controlplane.entity.Api;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.ApiRepository;
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
public class ApiService {

    private final ApiRepository apiRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public Api createApi(String orgId, CreateApiRequest request) {
        log.info("Creating API '{}' for organization: {}", request.getName(), orgId);

        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization not found with ID: " + orgId);
        }

        if (apiRepository.existsByOrgIdAndName(orgId, request.getName())) {
            throw new BusinessException("API with name '" + request.getName() + "' already exists in this organization");
        }

        Api api = Api.builder()
                .orgId(orgId)
                .name(request.getName())
                .displayName(request.getDisplayName())
                .build();

        Api saved = apiRepository.save(api);
        log.info("API created with ID: {}", saved.getId());
        return saved;
    }

    public Api getApiById(String apiId) {
        return apiRepository.findById(apiId)
                .orElseThrow(() -> new ResourceNotFoundException("API not found with ID: " + apiId));
    }

    public List<Api> getApisByOrg(String orgId) {
        return apiRepository.findByOrgId(orgId);
    }

    public Page<Api> getApisByOrg(String orgId, Pageable pageable) {
        return apiRepository.findByOrgId(orgId, pageable);
    }

    @Transactional
    public void deleteApi(String apiId) {
        Api api = getApiById(apiId);
        apiRepository.delete(api);
        log.info("API deleted: {}", apiId);
    }
}
