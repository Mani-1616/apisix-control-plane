package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateApiRequest;
import com.apisix.controlplane.entity.Environment;
import com.apisix.controlplane.entity.Upstream;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.UpstreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing environment-scoped upstreams
 * Upstreams are created immediately in APISIX when created in control plane
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpstreamService {

    private final UpstreamRepository upstreamRepository;
    private final EnvironmentService environmentService;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${apisix.admin-key:edd1c9f034335f136f87ad84b625c8f1}")
    private String adminKey;

    /**
     * Create a new upstream in both control plane and APISIX
     * Upstreams are scoped to environments
     */
    @Transactional
    public Upstream createUpstream(String environmentId, CreateApiRequest.UpstreamConfigDto config) {
        log.info("Creating upstream '{}' for environment: {}", config.getName(), environmentId);

        // Get environment to verify it exists and get APISIX URL
        Environment environment = environmentService.getEnvironmentById(environmentId);
        
        // Check if upstream with same name already exists in this environment
        if (upstreamRepository.existsByEnvironmentIdAndName(environmentId, config.getName())) {
            throw new BusinessException("Upstream with name '" + config.getName() + "' already exists in this environment");
        }

        // Create upstream entity
        Upstream upstream = Upstream.builder()
                .orgId(environment.getOrgId())
                .environmentId(environmentId)
                .name(config.getName())
                .description(config.getDescription())
                .targetUrl(config.getTargetUrl())
                .type(config.getType() != null ? config.getType() : "roundrobin")
                .config(config.getConfig())
                .inUse(false)
                .apisixStatus(Upstream.UpstreamStatus.SYNCING)
                .build();
        
        // Generate APISIX ID
        upstream.generateApisixId();
        
        // Save to MongoDB first
        Upstream saved = upstreamRepository.save(upstream);
        log.info("Created upstream entity with ID: {}, APISIX ID: {}", saved.getId(), saved.getApisixId());
        
        // Create in APISIX immediately
        try {
            createUpstreamInApisix(environment, saved);
            saved.setApisixStatus(Upstream.UpstreamStatus.ACTIVE);
            saved.setLastSyncedAt(LocalDateTime.now());
            upstreamRepository.save(saved);
            log.info("Successfully created upstream '{}' in APISIX at {}", saved.getName(), environment.getApisixAdminUrl());
        } catch (Exception e) {
            log.error("Failed to create upstream in APISIX", e);
            saved.setApisixStatus(Upstream.UpstreamStatus.FAILED);
            upstreamRepository.save(saved);
            throw new BusinessException("Upstream created in control plane but failed to create in APISIX: " + e.getMessage());
        }
        
        return saved;
    }

    /**
     * Create upstream in APISIX
     */
    private void createUpstreamInApisix(Environment environment, Upstream upstream) {
        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();
        
        Map<String, Object> payload = buildUpstreamPayload(upstream);
        
        String response = webClient.put()
                .uri("/apisix/admin/upstreams/{id}", upstream.getApisixId())
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("APISIX upstream creation failed with status {}: {}", resp.statusCode(), body);
                                    return Mono.error(new RuntimeException("APISIX returned " + resp.statusCode() + ": " + body));
                                }))
                .bodyToMono(String.class)
                .block();
        
        log.info("APISIX upstream creation response: {}", response);
    }

    /**
     * Build APISIX upstream payload
     */
    private Map<String, Object> buildUpstreamPayload(Upstream upstream) {
        Map<String, Object> payload = new HashMap<>();
        
        try {
            // Parse target URL
            java.net.URI uri = new java.net.URI(upstream.getTargetUrl());
            
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            String host = uri.getHost();
            int port = uri.getPort();
            
            // Use default ports if not specified
            if (port == -1) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            
            // Set load balancing type
            payload.put("type", upstream.getType());
            
            // Set scheme for HTTPS upstreams
            payload.put("scheme", scheme);
            
            // Set nodes with host:port format
            Map<String, Object> nodes = new HashMap<>();
            nodes.put(host + ":" + port, 1);
            payload.put("nodes", nodes);
            
            // Use 'node' to pass the upstream host (important for SSL/TLS with SNI)
            payload.put("pass_host", "node");
            
            // Add additional config if specified
            if (upstream.getConfig() != null && !upstream.getConfig().isEmpty()) {
                payload.putAll(upstream.getConfig());
            }
            
            // Add description
            if (upstream.getDescription() != null) {
                payload.put("desc", String.format("%s: %s", upstream.getName(), upstream.getDescription()));
            } else {
                payload.put("desc", upstream.getName());
            }
            
            log.debug("Built upstream payload: scheme={}, host={}:{}, type={}", scheme, host, port, upstream.getType());
            
        } catch (Exception e) {
            log.error("Failed to parse upstream URL: {}", upstream.getTargetUrl(), e);
            throw new RuntimeException("Invalid upstream URL format", e);
        }
        
        return payload;
    }

    /**
     * Get upstream by ID
     */
    public Upstream getUpstreamById(String upstreamId) {
        return upstreamRepository.findById(upstreamId)
                .orElseThrow(() -> new ResourceNotFoundException("Upstream not found with ID: " + upstreamId));
    }

    /**
     * Get all upstreams for an environment
     */
    public List<Upstream> getUpstreamsByEnvironment(String environmentId) {
        return upstreamRepository.findByEnvironmentId(environmentId);
    }

    /**
     * Get all upstreams in an organization (across all environments)
     */
    public List<Upstream> getUpstreamsByOrg(String orgId) {
        return upstreamRepository.findByOrgId(orgId);
    }

    /**
     * Mark upstream as in use
     */
    @Transactional
    public void markAsInUse(String upstreamId) {
        Upstream upstream = getUpstreamById(upstreamId);
        if (!upstream.isInUse()) {
            upstream.setInUse(true);
            upstreamRepository.save(upstream);
            log.info("Marked upstream '{}' as in use", upstream.getName());
        }
    }

    /**
     * Mark upstream as not in use (when last API revision using it is deleted)
     */
    @Transactional
    public void markAsUnused(String upstreamId) {
        Upstream upstream = getUpstreamById(upstreamId);
        if (upstream.isInUse()) {
            upstream.setInUse(false);
            upstreamRepository.save(upstream);
            log.info("Marked upstream '{}' as unused", upstream.getName());
        }
    }

    /**
     * Delete unused upstream (from both control plane and APISIX)
     */
    @Transactional
    public void deleteUpstream(String upstreamId) {
        Upstream upstream = getUpstreamById(upstreamId);
        
        if (upstream.isInUse()) {
            throw new BusinessException(
                String.format("Cannot delete upstream '%s' - it is currently in use by API revisions", 
                    upstream.getName())
            );
        }

        // Delete from APISIX
        try {
            Environment environment = environmentService.getEnvironmentById(upstream.getEnvironmentId());
            deleteUpstreamFromApisix(environment, upstream);
            log.info("Successfully deleted upstream from APISIX");
        } catch (Exception e) {
            log.warn("Failed to delete upstream from APISIX (will delete from control plane anyway): {}", e.getMessage());
        }

        // Delete from control plane
        upstreamRepository.delete(upstream);
        log.info("Deleted upstream '{}'", upstream.getName());
    }

    /**
     * Delete upstream from APISIX
     */
    private void deleteUpstreamFromApisix(Environment environment, Upstream upstream) {
        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();
        
        webClient.delete()
                .uri("/apisix/admin/upstreams/{id}", upstream.getApisixId())
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException("Failed to delete from APISIX: " + body))))
                .bodyToMono(String.class)
                .block();
    }
}
