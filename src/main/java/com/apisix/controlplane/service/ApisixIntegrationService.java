package com.apisix.controlplane.service;

import com.apisix.controlplane.entity.ApiRevision;
import com.apisix.controlplane.entity.Environment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to integrate with APISIX Admin API
 * Handles deployment and undeployment of services and routes to APISIX instances
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApisixIntegrationService {

    @Value("${apisix.admin.key}")
    private String adminKey;

    @Value("${apisix.admin.timeout:30000}")
    private int timeout;

    private final WebClient.Builder webClientBuilder;

    /**
     * Deploy service and routes to APISIX environment
     * Upstream is already created in APISIX (environment-scoped)
     */
    public void deployServiceAndRoutes(Environment environment, ApiRevision revision, 
                                      com.apisix.controlplane.entity.Upstream upstream) {
        log.info("Deploying service '{}' (revision {}) to APISIX at {} using upstream {}", 
                revision.getName(), revision.getRevisionNumber(), environment.getApisixAdminUrl(), upstream.getApisixId());

        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();

        String upstreamId = upstream.getApisixId();

        // Step 1: Create/Update Service in APISIX (references upstream)
        String serviceId = generateServiceId(environment.getOrgId(), environment.getId(), revision.getName());
        Map<String, Object> servicePayload = buildServicePayload(upstreamId, revision);
        
        try {
            log.info("Creating service {} with payload: {}", serviceId, servicePayload);
            String serviceResponse = webClient.put()
                    .uri("/apisix/admin/services/{id}", serviceId)
                    .bodyValue(servicePayload)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("APISIX service creation failed with status {}: {}", response.statusCode(), body);
                                        return Mono.error(new RuntimeException("APISIX returned " + response.statusCode() + ": " + body));
                                    }))
                    .bodyToMono(String.class)
                    .block();
            
            log.info("APISIX service creation response: {}", serviceResponse);
        } catch (Exception e) {
            log.error("Failed to create service in APISIX", e);
            throw new RuntimeException("Failed to create service in APISIX: " + e.getMessage(), e);
        }

        // Step 3: Create/Update Routes in APISIX
        int routeIndex = 0;
        for (ApiRevision.RouteConfig routeConfig : revision.getRoutes()) {
            String routeId = generateRouteId(environment.getOrgId(), environment.getId(), 
                    revision.getName(), routeConfig.getName(), routeIndex++);
            
            Map<String, Object> routePayload = buildRoutePayload(serviceId, routeConfig);
            
            try {
                log.info("Creating route {} with payload: {}", routeId, routePayload);
                String routeResponse = webClient.put()
                        .uri("/apisix/admin/routes/{id}", routeId)
                        .bodyValue(routePayload)
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                response -> response.bodyToMono(String.class)
                                        .flatMap(body -> {
                                            log.error("APISIX route creation failed with status {}: {}", response.statusCode(), body);
                                            return Mono.error(new RuntimeException("APISIX returned " + response.statusCode() + ": " + body));
                                        }))
                        .bodyToMono(String.class)
                        .block();
                
                log.info("APISIX route creation response: {}", routeResponse);
            } catch (Exception e) {
                log.error("Failed to create route '{}' in APISIX", routeConfig.getName(), e);
                throw new RuntimeException("Failed to create route '" + routeConfig.getName() + "' in APISIX: " + e.getMessage(), e);
            }
        }

        log.info("Successfully deployed upstream, service, and {} routes to APISIX", revision.getRoutes().size());
    }

    /**
     * Undeploy service and routes from APISIX environment
     * Upstream remains in APISIX (environment-scoped)
     */
    public void undeployServiceAndRoutes(Environment environment, ApiRevision revision,
                                        com.apisix.controlplane.entity.Upstream upstream) {
        log.info("Undeploying service '{}' (revision {}) from APISIX at {}", 
                revision.getName(), revision.getRevisionNumber(), environment.getApisixAdminUrl());

        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();

        String serviceId = generateServiceId(environment.getOrgId(), environment.getId(), revision.getName());

        // Step 1: Delete Routes first (must be done before deleting service)
        int routeIndex = 0;
        int deletedRoutes = 0;
        for (ApiRevision.RouteConfig routeConfig : revision.getRoutes()) {
            String routeId = generateRouteId(environment.getOrgId(), environment.getId(), 
                    revision.getName(), routeConfig.getName(), routeIndex++);
            
            if (tryDeleteRoute(webClient, routeId, routeConfig.getName())) {
                deletedRoutes++;
            }
        }

        log.info("Deleted {}/{} routes", deletedRoutes, revision.getRoutes().size());

        // Give APISIX a moment to process route deletions
        if (deletedRoutes > 0) {
            try {
                Thread.sleep(500); // 500ms delay to ensure routes are fully deleted
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Step 2: Delete Service (only after routes are deleted)
        if (!tryDeleteService(webClient, serviceId)) {
            throw new RuntimeException("Failed to delete service from APISIX: " + serviceId);
        }

        log.info("Successfully undeployed service and routes from APISIX");
    }

    /**
     * Build APISIX service payload (references upstream)
     */
    private Map<String, Object> buildServicePayload(String upstreamId, ApiRevision revision) {
        Map<String, Object> payload = new HashMap<>();
        
        // Reference the upstream object (not embedded)
        payload.put("upstream_id", upstreamId);
        
        // Enable websocket if needed
        payload.put("enable_websocket", false);
        
        // Add plugins if configured
        if (revision.getServiceConfig() != null && 
            revision.getServiceConfig().getPlugins() != null && 
            !revision.getServiceConfig().getPlugins().isEmpty()) {
            payload.put("plugins", revision.getServiceConfig().getPlugins());
        }
        
        // Add description from metadata
        if (revision.getServiceConfig() != null &&
            revision.getServiceConfig().getMetadata() != null && 
            !revision.getServiceConfig().getMetadata().isEmpty()) {
            payload.put("desc", revision.getServiceConfig().getMetadata().toString());
        } else {
            payload.put("desc", String.format("%s - Revision %d", revision.getName(), revision.getRevisionNumber()));
        }
        
        log.debug("Built service payload referencing upstream: {}", upstreamId);
        
        return payload;
    }

    /**
     * Build APISIX route payload
     */
    private Map<String, Object> buildRoutePayload(String serviceId, ApiRevision.RouteConfig routeConfig) {
        Map<String, Object> payload = new HashMap<>();
        
        // Link to service
        payload.put("service_id", serviceId);
        
        // Route name
        payload.put("name", routeConfig.getName());
        
        // HTTP methods
        if (routeConfig.getMethods() != null && !routeConfig.getMethods().isEmpty()) {
            payload.put("methods", routeConfig.getMethods());
        }
        
        // URIs - use 'uri' if single, 'uris' if multiple
        if (routeConfig.getUris() != null && !routeConfig.getUris().isEmpty()) {
            if (routeConfig.getUris().size() == 1) {
                payload.put("uri", routeConfig.getUris().get(0));
            } else {
                payload.put("uris", routeConfig.getUris());
            }
        }
        
        // Enable the route (1 = enabled, 0 = disabled)
        payload.put("status", 1);
        
        // Add route-specific plugins if configured
        if (routeConfig.getPlugins() != null && !routeConfig.getPlugins().isEmpty()) {
            payload.put("plugins", routeConfig.getPlugins());
        }
        
        // Add description from metadata
        if (routeConfig.getMetadata() != null && !routeConfig.getMetadata().isEmpty()) {
            payload.put("desc", routeConfig.getMetadata().toString());
        }
        
        log.debug("Built route payload: {}", payload);
        
        return payload;
    }

    /**
     * Generate unique service ID (max 64 chars for APISIX)
     * Uses same ID across all environments for the same org+API
     */
    private String generateServiceId(String orgId, String envId, String apiName) {
        // Create a hash without envId to ensure same ID across environments
        String fullId = String.format("%s-%s", orgId, apiName);
        String hash = Integer.toHexString(fullId.hashCode());
        
        // Format: cp-svc-{hash}-{apiName-shortened}
        String sanitizedApiName = apiName.replaceAll("[^a-zA-Z0-9-_]", "-").toLowerCase();
        // Remove consecutive hyphens and trim
        sanitizedApiName = sanitizedApiName.replaceAll("-+", "-").replaceAll("^-|-$", "");
        
        if (sanitizedApiName.length() > 20) {
            sanitizedApiName = sanitizedApiName.substring(0, 20);
        }
        
        String serviceId = String.format("cp-svc-%s-%s", hash, sanitizedApiName);
        
        // Ensure max 64 chars and clean ending
        if (serviceId.length() > 64) {
            serviceId = serviceId.substring(0, 64);
        }
        serviceId = serviceId.replaceAll("-$", ""); // Remove trailing hyphen if any
        
        log.debug("Generated service ID: {} (length: {}) - consistent across environments", 
                  serviceId, serviceId.length());
        return serviceId;
    }

    /**
     * Generate unique route ID (max 64 chars for APISIX)
     * Uses same ID across all environments for the same org+API+route
     */
    private String generateRouteId(String orgId, String envId, String apiName, String routeName, int index) {
        // Create a hash without envId to ensure same ID across environments
        String fullId = String.format("%s-%s-%s-%d", orgId, apiName, routeName, index);
        String hash = Integer.toHexString(fullId.hashCode());
        
        // Format: cp-rt-{hash}-{routeName-shortened}
        String sanitizedRouteName = routeName.replaceAll("[^a-zA-Z0-9-_]", "-").toLowerCase();
        // Remove consecutive hyphens and trim
        sanitizedRouteName = sanitizedRouteName.replaceAll("-+", "-").replaceAll("^-|-$", "");
        
        if (sanitizedRouteName.length() > 20) {
            sanitizedRouteName = sanitizedRouteName.substring(0, 20);
        }
        
        String routeId = String.format("cp-rt-%s-%s-%d", hash, sanitizedRouteName, index);
        
        // Ensure max 64 chars and clean ending
        if (routeId.length() > 64) {
            routeId = routeId.substring(0, 64);
        }
        routeId = routeId.replaceAll("-$", ""); // Remove trailing hyphen if any
        
        log.debug("Generated route ID: {} (length: {}) - consistent across environments", 
                  routeId, routeId.length());
        return routeId;
    }

    /**
     * Try to delete a route from APISIX
     */
    private boolean tryDeleteRoute(WebClient webClient, String routeId, String routeName) {
        try {
            log.info("Attempting to delete route: {}", routeId);
            String response = webClient.delete()
                    .uri("/apisix/admin/routes/{id}", routeId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            clientResponse -> {
                                log.warn("Route {} not found (404), may already be deleted", routeId);
                                return Mono.empty();
                            })
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("Failed to delete route {} - Status: {}, Response: {}", 
                                                routeId, clientResponse.statusCode(), body);
                                        return Mono.error(new RuntimeException("APISIX returned " + clientResponse.statusCode() + ": " + body));
                                    }))
                    .bodyToMono(String.class)
                    .block();
            
            log.info("Successfully deleted route: {} - Response: {}", routeId, response);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete route '{}' ({}): {}", routeName, routeId, e.getMessage());
            return false;
        }
    }

    /**
     * Try to delete a service from APISIX
     */
    private boolean tryDeleteService(WebClient webClient, String serviceId) {
        try {
            log.info("Attempting to delete service: {}", serviceId);
            String response = webClient.delete()
                    .uri("/apisix/admin/services/{id}", serviceId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            clientResponse -> {
                                log.warn("Service {} not found (404), may already be deleted", serviceId);
                                return Mono.empty();
                            })
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("Failed to delete service {} - Status: {}, Response: {}", 
                                                serviceId, clientResponse.statusCode(), body);
                                        return Mono.error(new RuntimeException("APISIX returned " + clientResponse.statusCode() + ": " + body));
                                    }))
                    .bodyToMono(String.class)
                    .block();
            
            log.info("Successfully deleted service: {} - Response: {}", serviceId, response);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete service {}: {}", serviceId, e.getMessage());
            return false;
        }
    }

}

