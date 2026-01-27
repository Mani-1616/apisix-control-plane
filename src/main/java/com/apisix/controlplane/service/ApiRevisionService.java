package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateApiRequest;
import com.apisix.controlplane.dto.DeploymentRequest;
import com.apisix.controlplane.entity.ApiRevision;
import com.apisix.controlplane.entity.EnvironmentDeploymentStatus;
import com.apisix.controlplane.enums.RevisionState;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.ApiRevisionRepository;
import com.apisix.controlplane.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiRevisionService {

    private final ApiRevisionRepository apiRevisionRepository;
    private final OrganizationRepository organizationRepository;
    private final EnvironmentService environmentService;
    private final ApisixIntegrationService apisixIntegrationService;
    private final UpstreamService upstreamService;

    @Transactional
    public ApiRevision createApi(String orgId, CreateApiRequest request) {
        log.info("Creating API '{}' for organization: {}", request.getName(), orgId);
        
        // Validate organization exists
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization not found with ID: " + orgId);
        }

        // Check if API with same name already exists in this org
        if (apiRevisionRepository.existsByOrgIdAndName(orgId, request.getName())) {
            throw new BusinessException("API with name '" + request.getName() + "' already exists in this organization. Please create a revision instead.");
        }

        // Validate that at least one route is provided
        if (request.getRoutes() == null || request.getRoutes().isEmpty()) {
            throw new BusinessException("At least one route is required for the API");
        }

        // Validate environment-upstream mappings if provided
        Map<String, EnvironmentDeploymentStatus> environments = new HashMap<>();
        if (request.getEnvironmentUpstreams() != null) {
            for (Map.Entry<String, String> entry : request.getEnvironmentUpstreams().entrySet()) {
                String envId = entry.getKey();
                String upstreamId = entry.getValue();
                
                // Validate environment exists
                environmentService.getEnvironmentById(envId);
                
                // Validate upstream exists and belongs to this environment
                var upstream = upstreamService.getUpstreamById(upstreamId);
                if (!upstream.getEnvironmentId().equals(envId)) {
                    throw new BusinessException("Upstream " + upstreamId + " does not belong to environment " + envId);
                }
                
                // Create environment status (DRAFT initially)
                environments.put(envId, EnvironmentDeploymentStatus.builder()
                        .status(RevisionState.DRAFT)
                        .upstreamId(upstreamId)
                        .build());
            }
        }

        // Create revision 1
        ApiRevision revision = ApiRevision.builder()
                .orgId(orgId)
                .name(request.getName())
                .description(request.getDescription())
                .revisionNumber(1)
                .state(RevisionState.DRAFT)
                .environments(environments)
                .serviceConfig(convertToServiceConfig(request.getServiceConfig()))
                .routes(convertToRouteConfigs(request.getRoutes()))
                .build();

        ApiRevision saved = apiRevisionRepository.save(revision);
        log.info("API revision created with ID: {} (Revision: {})", saved.getId(), saved.getRevisionNumber());
        return saved;
    }

    @Transactional
    public ApiRevision createRevision(String orgId, String apiName, CreateApiRequest request) {
        log.info("Creating new revision for API '{}' in organization: {}", apiName, orgId);
        
        // Validate organization exists
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization not found with ID: " + orgId);
        }

        // Check if API exists
        if (!apiRevisionRepository.existsByOrgIdAndName(orgId, apiName)) {
            throw new ResourceNotFoundException("API with name '" + apiName + "' not found in this organization");
        }

        // Get the latest revision number
        ApiRevision latestRevision = apiRevisionRepository
                .findFirstByOrgIdAndNameOrderByRevisionNumberDesc(orgId, apiName)
                .orElseThrow(() -> new ResourceNotFoundException("No revisions found for API: " + apiName));

        int newRevisionNumber = latestRevision.getRevisionNumber() + 1;

        // Validate that at least one route is provided
        if (request.getRoutes() == null || request.getRoutes().isEmpty()) {
            throw new BusinessException("At least one route is required for the API");
        }

        // Validate environment-upstream mappings if provided
        Map<String, EnvironmentDeploymentStatus> environments = new HashMap<>();
        if (request.getEnvironmentUpstreams() != null) {
            for (Map.Entry<String, String> entry : request.getEnvironmentUpstreams().entrySet()) {
                String envId = entry.getKey();
                String upstreamId = entry.getValue();
                
                // Validate environment exists
                environmentService.getEnvironmentById(envId);
                
                // Validate upstream exists and belongs to this environment
                var upstream = upstreamService.getUpstreamById(upstreamId);
                if (!upstream.getEnvironmentId().equals(envId)) {
                    throw new BusinessException("Upstream " + upstreamId + " does not belong to environment " + envId);
                }
                
                // Create environment status (DRAFT initially)
                environments.put(envId, EnvironmentDeploymentStatus.builder()
                        .status(RevisionState.DRAFT)
                        .upstreamId(upstreamId)
                        .build());
            }
        }

        // Create new revision
        ApiRevision newRevision = ApiRevision.builder()
                .orgId(orgId)
                .name(apiName)
                .description(request.getDescription())
                .revisionNumber(newRevisionNumber)
                .state(RevisionState.DRAFT)
                .environments(environments)
                .serviceConfig(convertToServiceConfig(request.getServiceConfig()))
                .routes(convertToRouteConfigs(request.getRoutes()))
                .build();

        ApiRevision saved = apiRevisionRepository.save(newRevision);
        log.info("New API revision created with ID: {} (Revision: {})", saved.getId(), saved.getRevisionNumber());
        return saved;
    }

    @Transactional
    public ApiRevision updateRevision(String revisionId, CreateApiRequest request) {
        ApiRevision revision = apiRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("API Revision not found with ID: " + revisionId));

        // Only allow updates to DRAFT revisions
        if (revision.getState() != RevisionState.DRAFT) {
            throw new BusinessException("Only DRAFT revisions can be updated. Current state: " + revision.getState());
        }

        // Validate that at least one route is provided
        if (request.getRoutes() == null || request.getRoutes().isEmpty()) {
            throw new BusinessException("At least one route is required for the API");
        }

        // Update environment-upstream mappings if provided
        // Only update mappings for environments that are still in DRAFT state
        if (request.getEnvironmentUpstreams() != null) {
            Map<String, EnvironmentDeploymentStatus> updatedEnvironments = new HashMap<>(revision.getEnvironments());
            
            for (Map.Entry<String, String> entry : request.getEnvironmentUpstreams().entrySet()) {
                String envId = entry.getKey();
                String upstreamId = entry.getValue();
                
                // Check if this environment is in DRAFT state or doesn't exist yet
                EnvironmentDeploymentStatus envStatus = updatedEnvironments.get(envId);
                if (envStatus != null && envStatus.getStatus() != RevisionState.DRAFT) {
                    throw new BusinessException("Cannot update upstream for environment " + envId + 
                            " - it is already in " + envStatus.getStatus() + " state");
                }
                
                // Validate environment exists
                environmentService.getEnvironmentById(envId);
                
                // Validate upstream exists and belongs to this environment
                var upstream = upstreamService.getUpstreamById(upstreamId);
                if (!upstream.getEnvironmentId().equals(envId)) {
                    throw new BusinessException("Upstream " + upstreamId + " does not belong to environment " + envId);
                }
                
                // Update or create environment status
                updatedEnvironments.put(envId, EnvironmentDeploymentStatus.builder()
                        .status(RevisionState.DRAFT)
                        .upstreamId(upstreamId)
                        .build());
            }
            
            revision.setEnvironments(updatedEnvironments);
        }

        // Update other fields
        revision.setDescription(request.getDescription());
        revision.setServiceConfig(convertToServiceConfig(request.getServiceConfig()));
        revision.setRoutes(convertToRouteConfigs(request.getRoutes()));

        ApiRevision updated = apiRevisionRepository.save(revision);
        log.info("API revision {} updated", revisionId);
        return updated;
    }

    @Transactional
    public ApiRevision cloneRevision(String revisionId) {
        log.info("Cloning revision: {}", revisionId);
        
        ApiRevision sourceRevision = apiRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("API Revision not found with ID: " + revisionId));
        
        // Get the latest revision number for this API
        ApiRevision latestRevision = apiRevisionRepository
                .findFirstByOrgIdAndNameOrderByRevisionNumberDesc(sourceRevision.getOrgId(), sourceRevision.getName())
                .orElse(sourceRevision);
        
        int newRevisionNumber = latestRevision.getRevisionNumber() + 1;
        
        // Clone the environment mappings but set all to DRAFT state
        Map<String, EnvironmentDeploymentStatus> clonedEnvironments = new HashMap<>();
        if (sourceRevision.getEnvironments() != null) {
            for (Map.Entry<String, EnvironmentDeploymentStatus> entry : sourceRevision.getEnvironments().entrySet()) {
                clonedEnvironments.put(entry.getKey(), EnvironmentDeploymentStatus.builder()
                        .status(RevisionState.DRAFT)
                        .upstreamId(entry.getValue().getUpstreamId())
                        .build());
            }
        }
        
        // Create cloned revision
        ApiRevision clonedRevision = ApiRevision.builder()
                .orgId(sourceRevision.getOrgId())
                .name(sourceRevision.getName())
                .description(sourceRevision.getDescription())
                .revisionNumber(newRevisionNumber)
                .state(RevisionState.DRAFT)
                .environments(clonedEnvironments)
                .serviceConfig(sourceRevision.getServiceConfig())
                .routes(sourceRevision.getRoutes())
                .build();
        
        ApiRevision saved = apiRevisionRepository.save(clonedRevision);
        log.info("Cloned revision created with ID: {} (Revision: {}) from source revision: {}", 
                saved.getId(), saved.getRevisionNumber(), revisionId);
        
        return saved;
    }

    @Transactional
    public void deleteRevision(String revisionId) {
        ApiRevision revision = apiRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("API Revision not found with ID: " + revisionId));

        // Only allow deletion of DRAFT revisions
        if (revision.getState() != RevisionState.DRAFT) {
            throw new BusinessException("Only DRAFT revisions can be deleted. Current state: " + revision.getState());
        }

        apiRevisionRepository.delete(revision);
        log.info("API revision {} deleted", revisionId);
    }

    @Transactional
    public ApiRevision deployRevision(String revisionId, DeploymentRequest request) {
        ApiRevision revision = apiRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("API Revision not found with ID: " + revisionId));

        log.info("Starting deployment of revision {} (Rev {}) to {} environment(s)", 
                revisionId, revision.getRevisionNumber(), request.getEnvironmentIds().size());

        // First, update revision's environment map with provided upstreams (if any)
        if (request.getEnvironmentUpstreams() != null && !request.getEnvironmentUpstreams().isEmpty()) {
            for (Map.Entry<String, String> entry : request.getEnvironmentUpstreams().entrySet()) {
                String envId = entry.getKey();
                String upstreamId = entry.getValue();
                
                // Validate upstream
                var upstream = upstreamService.getUpstreamById(upstreamId);
                if (!upstream.getEnvironmentId().equals(envId)) {
                    throw new BusinessException("Upstream does not belong to environment " + envId);
                }
                
                // Get or create environment status
                EnvironmentDeploymentStatus envStatus = revision.getEnvironments().get(envId);
                if (envStatus == null) {
                    // Create new entry with DRAFT status
                    revision.getEnvironments().put(envId, EnvironmentDeploymentStatus.builder()
                            .status(RevisionState.DRAFT)
                            .upstreamId(upstreamId)
                            .build());
                } else if (envStatus.getStatus() == RevisionState.DRAFT) {
                    // Only update upstream if status is DRAFT
                    envStatus.setUpstreamId(upstreamId);
                }
                // If status is not DRAFT, we keep the existing upstream (it will be validated below)
            }
            // Save the revision with updated upstream mappings
            revision = apiRevisionRepository.save(revision);
        }

        // Now deploy to each environment
        for (String envId : request.getEnvironmentIds()) {
            // Get environment
            var environment = environmentService.getEnvironmentById(envId);
            
            // Get environment status (should exist now after update above)
            EnvironmentDeploymentStatus envStatus = revision.getEnvironments().get(envId);
            
            if (envStatus == null || envStatus.getUpstreamId() == null) {
                throw new BusinessException("Upstream not configured for environment: " + envId);
            }
            
            String upstreamId = envStatus.getUpstreamId();
            
            // Validate upstream
            var upstream = upstreamService.getUpstreamById(upstreamId);
            if (!upstream.getEnvironmentId().equals(envId)) {
                throw new BusinessException("Upstream does not belong to environment " + envId);
            }
            
            // Check if this revision is already deployed to this environment
            if (envStatus.getStatus() == RevisionState.DEPLOYED && !request.isForce()) {
                throw new BusinessException("This revision is already deployed to environment " + envId + ". Use force deploy to redeploy.");
            }
            
            // Check if another revision of the same API is deployed to this environment
            List<ApiRevision> allRevisions = apiRevisionRepository.findByOrgIdAndNameOrderByRevisionNumberDesc(revision.getOrgId(), revision.getName());
            for (ApiRevision otherRevision : allRevisions) {
                if (!otherRevision.getId().equals(revisionId)) {
                    EnvironmentDeploymentStatus otherEnvStatus = otherRevision.getEnvironments().get(envId);
                    if (otherEnvStatus != null && otherEnvStatus.getStatus() == RevisionState.DEPLOYED) {
                        // Another revision is already deployed to this environment
                        if (!request.isForce()) {
                            // force=false: User must manually undeploy first
                            throw new BusinessException(
                                String.format("Another revision (Rev %d) is already deployed to environment '%s'. " +
                                    "Please undeploy it first or use force deploy to automatically undeploy and deploy.",
                                    otherRevision.getRevisionNumber(), environment.getName())
                            );
                        }
                        
                        // force=true: Backend automatically undeploys other revision first
                        log.info("Force deploy: Auto-undeploying revision {} (Rev {}) from environment {} before deploying revision {} (Rev {})", 
                                otherRevision.getId(), otherRevision.getRevisionNumber(), envId, revisionId, revision.getRevisionNumber());
                        
                        var otherUpstream = upstreamService.getUpstreamById(otherEnvStatus.getUpstreamId());
                        try {
                            // Delete from APISIX (force delete)
                            apisixIntegrationService.undeployServiceAndRoutes(environment, otherRevision, otherUpstream);
                            
                            // Update environment status in the map
                            otherEnvStatus.setStatus(RevisionState.UNDEPLOYED);
                            otherEnvStatus.setLastUndeployedAt(LocalDateTime.now());
                            
                            // Update parent revision state
                            otherRevision.calculateState();
                            apiRevisionRepository.save(otherRevision);
                            
                            log.info("Successfully auto-undeployed revision {} (Rev {}) from environment {}", 
                                    otherRevision.getId(), otherRevision.getRevisionNumber(), envId);
                        } catch (Exception e) {
                            log.error("Failed to auto-undeploy existing revision {} (Rev {}) from environment {}", 
                                    otherRevision.getId(), otherRevision.getRevisionNumber(), envId, e);
                            throw new BusinessException(
                                String.format("Failed to auto-undeploy existing revision (Rev %d): %s", 
                                    otherRevision.getRevisionNumber(), e.getMessage())
                            );
                        }
                    }
                }
            }
            
            // Now deploy the new revision to APISIX
            try {
                log.info("Deploying revision {} (Rev {}) to APISIX environment {}", 
                        revisionId, revision.getRevisionNumber(), envId);
                
                apisixIntegrationService.deployServiceAndRoutes(environment, revision, upstream);
                
                // Update environment status to DEPLOYED
                envStatus.setStatus(RevisionState.DEPLOYED);
                envStatus.setLastDeployedAt(LocalDateTime.now());
                
                log.info("Successfully deployed revision {} (Rev {}) to environment {}", 
                        revisionId, revision.getRevisionNumber(), envId);
            } catch (Exception e) {
                log.error("Failed to deploy revision {} (Rev {}) to environment {}", 
                        revisionId, revision.getRevisionNumber(), envId, e);
                throw new BusinessException("Deployment failed for environment " + envId + ": " + e.getMessage());
            }
        }
        
        // Recalculate overall revision state based on all environment statuses
        revision.calculateState();
        
        ApiRevision savedRevision = apiRevisionRepository.save(revision);
        log.info("Deployment complete. Revision {} (Rev {}) overall state: {}", 
                revisionId, revision.getRevisionNumber(), savedRevision.getState());
        
        return savedRevision;
    }

    @Transactional
    public ApiRevision undeployRevision(String revisionId, DeploymentRequest request) {
        ApiRevision revision = apiRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("API Revision not found with ID: " + revisionId));

        log.info("Starting undeployment of revision {} (Rev {}) from {} environment(s)", 
                revisionId, revision.getRevisionNumber(), request.getEnvironmentIds().size());

        // Undeploy from each environment
        for (String envId : request.getEnvironmentIds()) {
            // Get environment
            var environment = environmentService.getEnvironmentById(envId);
            
            // Check if deployed
            EnvironmentDeploymentStatus envStatus = revision.getEnvironments().get(envId);
            if (envStatus == null || envStatus.getStatus() != RevisionState.DEPLOYED) {
                log.warn("Revision {} (Rev {}) is not deployed to environment {} (status: {}), skipping", 
                        revisionId, revision.getRevisionNumber(), envId, 
                        envStatus != null ? envStatus.getStatus() : "NULL");
                continue;
            }
            
            // Get upstream
            var upstream = upstreamService.getUpstreamById(envStatus.getUpstreamId());
            
            // Force delete from APISIX (remove service and routes)
            try {
                log.info("Undeploying revision {} (Rev {}) from APISIX environment {}", 
                        revisionId, revision.getRevisionNumber(), envId);
                
                apisixIntegrationService.undeployServiceAndRoutes(environment, revision, upstream);
                
                // Update environment status flag in the map
                envStatus.setStatus(RevisionState.UNDEPLOYED);
                envStatus.setLastUndeployedAt(LocalDateTime.now());
                
                log.info("Successfully undeployed revision {} (Rev {}) from environment {}", 
                        revisionId, revision.getRevisionNumber(), envId);
            } catch (Exception e) {
                log.error("Failed to undeploy revision {} (Rev {}) from environment {}", 
                        revisionId, revision.getRevisionNumber(), envId, e);
                throw new BusinessException(
                    String.format("Undeployment failed for environment %s: %s", envId, e.getMessage())
                );
            }
        }
        
        // Recalculate overall parent revision state based on all environment statuses
        revision.calculateState();
        
        ApiRevision savedRevision = apiRevisionRepository.save(revision);
        log.info("Undeployment complete. Revision {} (Rev {}) overall state: {}", 
                revisionId, revision.getRevisionNumber(), savedRevision.getState());
        
        return savedRevision;
    }

    public ApiRevision getRevisionById(String revisionId) {
        return apiRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("API Revision not found with ID: " + revisionId));
    }

    public List<ApiRevision> getAllApisInOrg(String orgId) {
        return apiRevisionRepository.findByOrgId(orgId);
    }

    public List<ApiRevision> getApiRevisions(String orgId, String apiName) {
        return apiRevisionRepository.findByOrgIdAndNameOrderByRevisionNumberDesc(orgId, apiName);
    }

    // Helper methods
    private ApiRevision.ServiceConfig convertToServiceConfig(CreateApiRequest.ServiceConfigDto dto) {
        if (dto == null) {
            return ApiRevision.ServiceConfig.builder().build();
        }
        return ApiRevision.ServiceConfig.builder()
                .plugins(dto.getPlugins())
                .metadata(dto.getMetadata())
                .build();
    }

    private List<ApiRevision.RouteConfig> convertToRouteConfigs(List<CreateApiRequest.RouteConfigDto> dtos) {
        if (dtos == null) {
            return new ArrayList<>();
        }
        return dtos.stream()
                .map(dto -> ApiRevision.RouteConfig.builder()
                        .name(dto.getName())
                        .methods(dto.getMethods())
                        .uris(dto.getUris())
                        .plugins(dto.getPlugins())
                        .metadata(dto.getMetadata())
                        .build())
                .collect(Collectors.toList());
    }
}
