package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateDeveloperRequest;
import com.apisix.controlplane.entity.Developer;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.DeveloperRepository;
import com.apisix.controlplane.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeveloperService {
    
    private final DeveloperRepository developerRepository;
    private final OrganizationRepository organizationRepository;
    
    @Transactional
    public Developer createDeveloper(String orgId, CreateDeveloperRequest request) {
        log.info("Creating developer with email {} in organization {}", request.getEmail(), orgId);
        
        // Validate organization exists
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization not found: " + orgId);
        }
        
        // Check if developer with same email already exists in this org
        if (developerRepository.existsByOrgIdAndEmail(orgId, request.getEmail())) {
            throw new BusinessException("Developer with email " + request.getEmail() + 
                    " already exists in this organization");
        }
        
        Developer developer = Developer.builder()
                .orgId(orgId)
                .email(request.getEmail())
                .name(request.getName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        Developer saved = developerRepository.save(developer);
        log.info("Developer created successfully with ID: {}", saved.getId());
        
        return saved;
    }
    
    public List<Developer> getDevelopersByOrganization(String orgId) {
        log.info("Fetching all developers for organization: {}", orgId);
        
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization not found: " + orgId);
        }
        
        return developerRepository.findByOrgId(orgId);
    }
    
    public Developer getDeveloperById(String orgId, String developerId) {
        log.info("Fetching developer {} in organization {}", developerId, orgId);
        
        Developer developer = developerRepository.findById(developerId)
                .orElseThrow(() -> new ResourceNotFoundException("Developer not found: " + developerId));
        
        if (!developer.getOrgId().equals(orgId)) {
            throw new BusinessException("Developer does not belong to this organization");
        }
        
        return developer;
    }
    
    @Transactional
    public Developer updateDeveloper(String orgId, String developerId, CreateDeveloperRequest request) {
        log.info("Updating developer {} in organization {}", developerId, orgId);
        
        Developer developer = getDeveloperById(orgId, developerId);
        
        // Check if email is being changed and if new email already exists
        if (!developer.getEmail().equals(request.getEmail())) {
            if (developerRepository.existsByOrgIdAndEmail(orgId, request.getEmail())) {
                throw new BusinessException("Developer with email " + request.getEmail() + 
                        " already exists in this organization");
            }
            developer.setEmail(request.getEmail());
        }
        
        developer.setName(request.getName());
        developer.setUpdatedAt(LocalDateTime.now());
        
        Developer updated = developerRepository.save(developer);
        log.info("Developer updated successfully: {}", developerId);
        
        return updated;
    }
    
    @Transactional
    public void deleteDeveloper(String orgId, String developerId) {
        log.info("Deleting developer {} from organization {}", developerId, orgId);
        
        Developer developer = getDeveloperById(orgId, developerId);
        
        // Note: Before deleting, caller should ensure all subscriptions are revoked
        // This will be handled in the controller layer
        
        developerRepository.delete(developer);
        log.info("Developer deleted successfully: {}", developerId);
    }
}

