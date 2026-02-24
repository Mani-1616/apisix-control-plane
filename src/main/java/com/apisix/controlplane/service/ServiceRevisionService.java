package com.apisix.controlplane.service;

import com.apisix.controlplane.apisix.model.RouteSpec;
import com.apisix.controlplane.dto.CreateServiceRevisionRequest;
import com.apisix.controlplane.dto.DeploymentRequest;
import com.apisix.controlplane.dto.DeploymentResponse;
import com.apisix.controlplane.dto.PaginatedResponse;
import com.apisix.controlplane.dto.EnvironmentUpstreamMapping;
import com.apisix.controlplane.dto.UpdateRevisionSpecsRequest;
import com.apisix.controlplane.dto.UpdateUpstreamBindingsRequest;
import com.apisix.controlplane.dto.RevisionSummary;
import com.apisix.controlplane.dto.ServiceRevisionResponse;
import com.apisix.controlplane.dto.UpstreamBindingResponse;
import com.apisix.controlplane.entity.*;
import com.apisix.controlplane.enums.RevisionState;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.DeploymentRepository;
import com.apisix.controlplane.repository.EnvironmentRepository;
import com.apisix.controlplane.repository.ServiceRevisionRepository;
import com.apisix.controlplane.repository.UpstreamBindingRepository;
import com.apisix.controlplane.repository.UpstreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class ServiceRevisionService {

    private final ServiceRevisionRepository revisionRepository;
    private final DeploymentRepository deploymentRepository;
    private final UpstreamBindingRepository upstreamBindingRepository;
    private final EnvironmentRepository environmentRepository;
    private final UpstreamRepository upstreamRepository;
    private final ApiServiceService apiServiceService;
    private final EnvironmentService environmentService;
    private final ApisixIntegrationService apisixIntegrationService;
    private final UpstreamService upstreamService;

    @Transactional
    public ServiceRevisionResponse createRevision(String serviceId, CreateServiceRevisionRequest request) {
        log.info("Creating revision for service: {}", serviceId);

        Service service = apiServiceService.getServiceById(serviceId);

        // Determine next revision number
        int nextRevisionNumber = revisionRepository
                .findFirstByServiceIdOrderByRevisionNumberDesc(serviceId)
                .map(rev -> rev.getRevisionNumber() + 1)
                .orElse(1);

        // Validate routes
        if (request.getRouteSpecifications() == null || request.getRouteSpecifications().isEmpty()) {
            throw new BusinessException("At least one route specification is required");
        }

        // Stamp each route with the APISIX service_id (Postgres service UUID)
        stampServiceIdOnRoutes(request.getRouteSpecifications(), serviceId);

        ServiceRevision revision = ServiceRevision.builder()
                .orgId(service.getOrgId())
                .serviceId(serviceId)
                .revisionNumber(nextRevisionNumber)
                .state(RevisionState.INACTIVE)
                .serviceSpecification(request.getServiceSpecification())
                .routeSpecifications(request.getRouteSpecifications())
                .build();

        ServiceRevision saved = revisionRepository.save(revision);

        // Create upstream bindings from environment-upstream mappings
        List<UpstreamBinding> bindings = new ArrayList<>();
        if (request.getEnvironmentUpstreams() != null) {
            for (EnvironmentUpstreamMapping mapping : request.getEnvironmentUpstreams()) {
                String envId = mapping.getEnvironmentId();
                String upstreamId = mapping.getUpstreamId();

                environmentService.getEnvironmentById(envId);
                var upstream = upstreamService.getUpstreamById(upstreamId);
                if (!upstream.getEnvironmentId().equals(envId)) {
                    throw new BusinessException("Upstream " + upstreamId + " does not belong to environment " + envId);
                }

                UpstreamBinding binding = UpstreamBinding.builder()
                        .orgId(service.getOrgId())
                        .serviceId(serviceId)
                        .revisionId(saved.getId())
                        .environmentId(envId)
                        .upstreamId(upstreamId)
                        .build();
                bindings.add(upstreamBindingRepository.save(binding));
            }
        }

        log.info("Revision created with ID: {} (Rev {})", saved.getId(), saved.getRevisionNumber());

        // Resolve names for upstream binding responses
        List<String> envIds = bindings.stream().map(UpstreamBinding::getEnvironmentId).distinct().toList();
        List<String> upsIds = bindings.stream().map(UpstreamBinding::getUpstreamId).distinct().toList();
        Map<String, Environment> envMap = environmentRepository.findAllById(envIds)
                .stream().collect(Collectors.toMap(Environment::getId, Function.identity()));
        Map<String, Upstream> upstreamMap = upstreamRepository.findAllById(upsIds)
                .stream().collect(Collectors.toMap(Upstream::getId, Function.identity()));

        return ServiceRevisionResponse.fromEntity(saved, List.of(), toBindingResponses(bindings, envMap, upstreamMap));
    }

    @Transactional
    public ServiceRevisionResponse updateRevisionSpecs(String revisionId, UpdateRevisionSpecsRequest request) {
        ServiceRevision revision = findRevisionById(revisionId);

        if (revision.getState() != RevisionState.INACTIVE) {
            throw new BusinessException("Service and route specifications can only be updated for INACTIVE revisions. Current state: " + revision.getState());
        }

        if (request.getRouteSpecifications() == null || request.getRouteSpecifications().isEmpty()) {
            throw new BusinessException("At least one route specification is required");
        }

        stampServiceIdOnRoutes(request.getRouteSpecifications(), revision.getServiceId());

        revision.setServiceSpecification(request.getServiceSpecification());
        revision.setRouteSpecifications(request.getRouteSpecifications());

        ServiceRevision updated = revisionRepository.save(revision);
        log.info("Revision {} specs updated", revisionId);
        return toResponse(updated);
    }

    @Transactional
    public ServiceRevisionResponse updateUpstreamBindings(String revisionId, UpdateUpstreamBindingsRequest request) {
        ServiceRevision revision = findRevisionById(revisionId);

        // For ACTIVE revisions, only allow binding changes for non-deployed environments
        Set<String> deployedEnvIds = Set.of();
        if (revision.getState() == RevisionState.ACTIVE) {
            deployedEnvIds = deploymentRepository.findByRevisionId(revisionId).stream()
                    .map(Deployment::getEnvironmentId)
                    .collect(Collectors.toSet());
        }

        for (EnvironmentUpstreamMapping mapping : request.getEnvironmentUpstreams()) {
            String envId = mapping.getEnvironmentId();
            String upstreamId = mapping.getUpstreamId();

            if (deployedEnvIds.contains(envId)) {
                throw new BusinessException("Cannot modify upstream binding for environment " + envId
                        + " because the revision is currently deployed there. Undeploy first.");
            }

            environmentService.getEnvironmentById(envId);
            var upstream = upstreamService.getUpstreamById(upstreamId);
            if (!upstream.getEnvironmentId().equals(envId)) {
                throw new BusinessException("Upstream " + upstreamId + " does not belong to environment " + envId);
            }

            upsertUpstreamBinding(revision.getOrgId(), revision.getServiceId(), revisionId, envId, upstreamId);
        }

        log.info("Revision {} upstream bindings updated", revisionId);
        return toResponse(revision);
    }

    @Transactional
    public ServiceRevisionResponse cloneRevision(String revisionId) {
        log.info("Cloning revision: {}", revisionId);

        ServiceRevision source = findRevisionById(revisionId);

        int nextRevisionNumber = revisionRepository
                .findFirstByServiceIdOrderByRevisionNumberDesc(source.getServiceId())
                .map(rev -> rev.getRevisionNumber() + 1)
                .orElse(source.getRevisionNumber() + 1);

        ServiceRevision cloned = ServiceRevision.builder()
                .orgId(source.getOrgId())
                .serviceId(source.getServiceId())
                .revisionNumber(nextRevisionNumber)
                .state(RevisionState.INACTIVE)
                .serviceSpecification(source.getServiceSpecification())
                .routeSpecifications(source.getRouteSpecifications())
                .build();

        ServiceRevision saved = revisionRepository.save(cloned);

        // Copy upstream bindings from source revision
        List<UpstreamBinding> sourceBindings = upstreamBindingRepository.findByRevisionId(revisionId);
        List<UpstreamBinding> clonedBindings = new ArrayList<>();
        for (UpstreamBinding sourceBinding : sourceBindings) {
            UpstreamBinding newBinding = UpstreamBinding.builder()
                    .orgId(sourceBinding.getOrgId())
                    .serviceId(sourceBinding.getServiceId())
                    .revisionId(saved.getId())
                    .environmentId(sourceBinding.getEnvironmentId())
                    .upstreamId(sourceBinding.getUpstreamId())
                    .build();
            clonedBindings.add(upstreamBindingRepository.save(newBinding));
        }

        log.info("Cloned revision: {} (Rev {}) from {}", saved.getId(), saved.getRevisionNumber(), revisionId);

        // Resolve names for upstream binding responses
        List<String> envIds = clonedBindings.stream().map(UpstreamBinding::getEnvironmentId).distinct().toList();
        List<String> upsIds = clonedBindings.stream().map(UpstreamBinding::getUpstreamId).distinct().toList();
        Map<String, Environment> envMap = environmentRepository.findAllById(envIds)
                .stream().collect(Collectors.toMap(Environment::getId, Function.identity()));
        Map<String, Upstream> upstreamMap = upstreamRepository.findAllById(upsIds)
                .stream().collect(Collectors.toMap(Upstream::getId, Function.identity()));

        return ServiceRevisionResponse.fromEntity(saved, List.of(), toBindingResponses(clonedBindings, envMap, upstreamMap));
    }

    @Transactional
    public void deleteRevision(String revisionId) {
        ServiceRevision revision = findRevisionById(revisionId);

        if (revision.getState() != RevisionState.INACTIVE) {
            throw new BusinessException("Only INACTIVE revisions can be deleted. Current state: " + revision.getState());
        }

        upstreamBindingRepository.deleteByRevisionId(revisionId);
        revisionRepository.delete(revision);
        log.info("Revision {} deleted", revisionId);
    }

    @Transactional
    public ServiceRevisionResponse deployRevision(String revisionId, DeploymentRequest request) {
        ServiceRevision revision = findRevisionById(revisionId);
        Service service = apiServiceService.getServiceById(revision.getServiceId());

        String envId = request.getEnvironmentId();
        log.info("Deploying revision {} (Rev {}) of service '{}' to environment {}",
                revisionId, revision.getRevisionNumber(), service.getName(), envId);

        // Upstream bindings must be set beforehand via the update upstream bindings endpoint.
        var environment = environmentService.getEnvironmentById(envId);

        // Resolve upstream from UpstreamBinding (single source of truth)
        UpstreamBinding binding = upstreamBindingRepository
                .findByRevisionIdAndEnvironmentId(revisionId, envId)
                .orElseThrow(() -> new BusinessException("Upstream not configured for environment: " + envId));

        var upstream = upstreamService.getUpstreamById(binding.getUpstreamId());

        // Check for conflicting deployment (another revision deployed to same service+env)
        Optional<Deployment> existingDeployment = deploymentRepository
                .findByServiceIdAndEnvironmentId(revision.getServiceId(), envId);

        if (existingDeployment.isPresent()) {
            Deployment existing = existingDeployment.get();
            if (existing.getRevisionId().equals(revisionId) && !request.isForce()) {
                throw new BusinessException("Already deployed to environment " + envId + ". Use force to redeploy.");
            }

            if (!request.isForce()) {
                ServiceRevision otherRevision = findRevisionById(existing.getRevisionId());
                throw new BusinessException(
                        String.format("Another revision (Rev %d) is already deployed to environment '%s'. " +
                                        "Undeploy it first or use force deploy.",
                                otherRevision.getRevisionNumber(), environment.getName()));
            }

            // Force: auto-undeploy old revision
            log.info("Force deploy: auto-undeploying revision {} from env {}", existing.getRevisionId(), envId);
            ServiceRevision oldRevision = findRevisionById(existing.getRevisionId());
            UpstreamBinding oldBinding = upstreamBindingRepository
                    .findByRevisionIdAndEnvironmentId(existing.getRevisionId(), envId)
                    .orElse(null);

            if (oldBinding != null) {
                var oldUpstream = upstreamService.getUpstreamById(oldBinding.getUpstreamId());
                try {
                    apisixIntegrationService.undeployServiceAndRoutes(environment, oldRevision, service, oldUpstream);
                } catch (Exception e) {
                    throw new BusinessException("Failed to auto-undeploy old revision: " + e.getMessage());
                }
            }

            deploymentRepository.delete(existing);
            recalculateState(oldRevision);
            revisionRepository.save(oldRevision);
        }

        // Deploy to APISIX
        try {
            apisixIntegrationService.deployServiceAndRoutes(environment, revision, service, upstream);
        } catch (Exception e) {
            throw new BusinessException("Deployment failed for environment " + envId + ": " + e.getMessage());
        }

        // Create Deployment record
        Deployment deployment = Deployment.builder()
                .orgId(revision.getOrgId())
                .serviceId(revision.getServiceId())
                .revisionId(revisionId)
                .environmentId(envId)
                .build();
        deploymentRepository.save(deployment);

        // Update revision state
        revision.setState(RevisionState.ACTIVE);
        ServiceRevision saved = revisionRepository.save(revision);

        log.info("Deployment complete. Rev {} state: {}", revision.getRevisionNumber(), saved.getState());
        return toResponse(saved);
    }

    @Transactional
    public ServiceRevisionResponse undeployRevision(String revisionId, DeploymentRequest request) {
        ServiceRevision revision = findRevisionById(revisionId);
        Service service = apiServiceService.getServiceById(revision.getServiceId());

        String envId = request.getEnvironmentId();
        log.info("Undeploying revision {} (Rev {}) from environment {}",
                revisionId, revision.getRevisionNumber(), envId);

        var environment = environmentService.getEnvironmentById(envId);

        Optional<Deployment> existingDeployment = deploymentRepository
                .findByServiceIdAndEnvironmentId(revision.getServiceId(), envId);

        if (existingDeployment.isEmpty() || !existingDeployment.get().getRevisionId().equals(revisionId)) {
            log.warn("Rev {} not deployed to env {}, skipping", revision.getRevisionNumber(), envId);
        } else {
            // Resolve upstream from binding
            UpstreamBinding binding = upstreamBindingRepository
                    .findByRevisionIdAndEnvironmentId(revisionId, envId)
                    .orElse(null);

            if (binding != null) {
                var upstream = upstreamService.getUpstreamById(binding.getUpstreamId());
                try {
                    apisixIntegrationService.undeployServiceAndRoutes(environment, revision, service, upstream);
                } catch (Exception e) {
                    throw new BusinessException("Undeployment failed for environment " + envId + ": " + e.getMessage());
                }
            }

            deploymentRepository.delete(existingDeployment.get());
        }

        // Recalculate state
        recalculateState(revision);
        ServiceRevision saved = revisionRepository.save(revision);

        log.info("Undeployment complete. Rev {} state: {}", revision.getRevisionNumber(), saved.getState());
        return toResponse(saved);
    }

    public ServiceRevisionResponse getRevisionById(String revisionId) {
        return toResponse(findRevisionById(revisionId));
    }

    public List<ServiceRevisionResponse> getRevisionsByService(String serviceId) {
        List<ServiceRevision> revisions = revisionRepository.findByServiceIdOrderByRevisionNumberDesc(serviceId);
        return revisions.stream().map(this::toResponse).toList();
    }

    public PaginatedResponse<ServiceRevisionResponse> getRevisionsByService(String serviceId, Pageable pageable) {
        Page<ServiceRevision> page = revisionRepository.findByServiceId(serviceId, pageable);
        List<ServiceRevisionResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return PaginatedResponse.from(page, content);
    }

    /**
     * Batch-fetch revision summaries (without specs) for multiple services at once.
     * Returns a map of serviceId to its list of RevisionSummary DTOs.
     */
    public Map<String, List<RevisionSummary>> getRevisionSummariesByServiceIds(List<String> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return Map.of();
        }

        List<ServiceRevision> allRevisions = revisionRepository
                .findByServiceIdInOrderByServiceIdAscRevisionNumberDesc(serviceIds);

        if (allRevisions.isEmpty()) {
            return serviceIds.stream().collect(Collectors.toMap(id -> id, id -> List.of()));
        }

        // Collect all revision IDs for batch lookups
        List<String> revisionIds = allRevisions.stream().map(ServiceRevision::getId).toList();

        // Batch-fetch deployments and upstream bindings
        List<Deployment> allDeployments = new ArrayList<>();
        List<UpstreamBinding> allBindings = new ArrayList<>();
        for (String revId : revisionIds) {
            allDeployments.addAll(deploymentRepository.findByRevisionId(revId));
            allBindings.addAll(upstreamBindingRepository.findByRevisionId(revId));
        }

        // Collect all environment and upstream IDs for name resolution
        List<String> envIds = new ArrayList<>();
        allDeployments.forEach(d -> envIds.add(d.getEnvironmentId()));
        allBindings.forEach(b -> envIds.add(b.getEnvironmentId()));

        List<String> upstreamIds = allBindings.stream()
                .map(UpstreamBinding::getUpstreamId)
                .distinct()
                .toList();

        Map<String, Environment> envMap = envIds.isEmpty() ? Map.of() :
                environmentRepository.findAllById(envIds.stream().distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(Environment::getId, Function.identity()));

        Map<String, Upstream> upstreamMap = upstreamIds.isEmpty() ? Map.of() :
                upstreamRepository.findAllById(upstreamIds)
                        .stream()
                        .collect(Collectors.toMap(Upstream::getId, Function.identity()));

        // Group deployments and bindings by revision ID
        Map<String, List<Deployment>> deploymentsByRevision = allDeployments.stream()
                .collect(Collectors.groupingBy(Deployment::getRevisionId));

        Map<String, List<UpstreamBinding>> bindingsByRevision = allBindings.stream()
                .collect(Collectors.groupingBy(UpstreamBinding::getRevisionId));

        // Build RevisionSummary DTOs and group by service ID
        Map<String, List<RevisionSummary>> result = new java.util.LinkedHashMap<>();
        for (ServiceRevision revision : allRevisions) {
            List<Deployment> deps = deploymentsByRevision.getOrDefault(revision.getId(), List.of());
            List<UpstreamBinding> binds = bindingsByRevision.getOrDefault(revision.getId(), List.of());

            List<DeploymentResponse> depResponses = deps.stream()
                    .map(d -> {
                        String envName = Optional.ofNullable(envMap.get(d.getEnvironmentId()))
                                .map(Environment::getName).orElse(null);
                        return DeploymentResponse.from(d, envName);
                    })
                    .toList();

            List<UpstreamBindingResponse> bindResponses = toBindingResponses(binds, envMap, upstreamMap);

            RevisionSummary summary = RevisionSummary.fromEntity(revision, depResponses, bindResponses);
            result.computeIfAbsent(revision.getServiceId(), k -> new ArrayList<>()).add(summary);
        }

        return result;
    }

    private ServiceRevision findRevisionById(String revisionId) {
        return revisionRepository.findById(revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("Service revision not found with ID: " + revisionId));
    }

    // --- Helper methods ---

    /**
     * Recalculate revision state based on Deployment records.
     */
    private void recalculateState(ServiceRevision revision) {
        if (deploymentRepository.existsByRevisionId(revision.getId())) {
            revision.setState(RevisionState.ACTIVE);
        } else {
            revision.setState(RevisionState.INACTIVE);
        }
    }

    /**
     * Upsert an upstream binding for a revision + environment.
     */
    private void upsertUpstreamBinding(String orgId, String serviceId, String revisionId, String envId, String upstreamId) {
        Optional<UpstreamBinding> existing = upstreamBindingRepository
                .findByRevisionIdAndEnvironmentId(revisionId, envId);

        if (existing.isPresent()) {
            UpstreamBinding binding = existing.get();
            binding.setUpstreamId(upstreamId);
            upstreamBindingRepository.save(binding);
        } else {
            UpstreamBinding binding = UpstreamBinding.builder()
                    .orgId(orgId)
                    .serviceId(serviceId)
                    .revisionId(revisionId)
                    .environmentId(envId)
                    .upstreamId(upstreamId)
                    .build();
            upstreamBindingRepository.save(binding);
        }
    }

    /**
     * Build a response DTO from a revision entity, populating deployments and upstream bindings
     * with resolved environment and upstream names.
     */
    private ServiceRevisionResponse toResponse(ServiceRevision revision) {
        List<Deployment> deployments = deploymentRepository.findByRevisionId(revision.getId());
        List<UpstreamBinding> bindings = upstreamBindingRepository.findByRevisionId(revision.getId());

        // Collect all referenced environment IDs and upstream IDs for batch lookup
        List<String> envIds = new ArrayList<>();
        deployments.forEach(d -> envIds.add(d.getEnvironmentId()));
        bindings.forEach(b -> envIds.add(b.getEnvironmentId()));

        List<String> upstreamIds = bindings.stream()
                .map(UpstreamBinding::getUpstreamId)
                .distinct()
                .toList();

        Map<String, Environment> envMap = environmentRepository.findAllById(envIds.stream().distinct().toList())
                .stream()
                .collect(Collectors.toMap(Environment::getId, Function.identity()));

        Map<String, Upstream> upstreamMap = upstreamRepository.findAllById(upstreamIds)
                .stream()
                .collect(Collectors.toMap(Upstream::getId, Function.identity()));

        List<DeploymentResponse> deploymentResponses = deployments.stream()
                .map(d -> {
                    String envName = Optional.ofNullable(envMap.get(d.getEnvironmentId()))
                            .map(Environment::getName).orElse(null);
                    return DeploymentResponse.from(d, envName);
                })
                .toList();

        List<UpstreamBindingResponse> bindingResponses = toBindingResponses(bindings, envMap, upstreamMap);

        return ServiceRevisionResponse.fromEntity(revision, deploymentResponses, bindingResponses);
    }

    /**
     * Map a list of UpstreamBinding entities to UpstreamBindingResponse DTOs
     * using pre-fetched environment and upstream maps.
     */
    private List<UpstreamBindingResponse> toBindingResponses(List<UpstreamBinding> bindings,
                                                              Map<String, Environment> envMap,
                                                              Map<String, Upstream> upstreamMap) {
        return bindings.stream()
                .map(b -> {
                    String envName = Optional.ofNullable(envMap.get(b.getEnvironmentId()))
                            .map(Environment::getName).orElse(null);
                    String upsName = Optional.ofNullable(upstreamMap.get(b.getUpstreamId()))
                            .map(Upstream::getName).orElse(null);
                    return UpstreamBindingResponse.from(b, envName, upsName);
                })
                .toList();
    }

    /**
     * Set the APISIX service_id on every route specification.
     * Uses the Postgres ApiService UUID as the APISIX service identifier,
     * satisfying the schema requirement that each route references a service.
     */
    private void stampServiceIdOnRoutes(List<RouteSpec> routes, String serviceId) {
        if (routes == null) return;
        for (RouteSpec route : routes) {
            route.setServiceId(serviceId);
        }
    }
}
