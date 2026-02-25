package com.apisix.controlplane.service;

import com.apisix.controlplane.apisix.model.RouteSpec;
import com.apisix.controlplane.entity.Service;
import com.apisix.controlplane.entity.Environment;
import com.apisix.controlplane.entity.ServiceRevision;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles deployment and undeployment of services and routes to APISIX instances.
 * Payloads are built from strongly-typed spec objects.
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class ApisixIntegrationService {

    @Value("${apisix.admin.key}")
    private String adminKey;

    @Value("${apisix.admin.timeout:30000}")
    private int timeout;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * Deploy service and routes to an APISIX environment.
     */
    public void deployServiceAndRoutes(Environment environment, ServiceRevision revision,
                                       Service service,
                                       com.apisix.controlplane.entity.Upstream upstream) {
        log.info("Deploying service '{}' (Rev {}) to APISIX at {} using upstream {}",
                service.getName(), revision.getRevisionNumber(),
                environment.getApisixAdminUrl(), upstream.getApisixId());

        WebClient webClient = buildWebClient(environment);
        String upstreamId = upstream.getApisixId();

        // Step 1: Create/Update APISIX Service (using Postgres service UUID as APISIX service ID)
        String serviceId = service.getId();
        Map<String, Object> servicePayload = buildServicePayload(upstreamId, revision, service);

        try {
            log.info("Creating service {} with payload: {}", serviceId, servicePayload);
            String response = webClient.put()
                    .uri("/apisix/admin/services/{id}", serviceId)
                    .bodyValue(servicePayload)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("APISIX service creation failed: {} {}", resp.statusCode(), body);
                                        return Mono.error(new RuntimeException("APISIX returned " + resp.statusCode() + ": " + body));
                                    }))
                    .bodyToMono(String.class)
                    .block();
            log.info("APISIX service response: {}", response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create service in APISIX: " + e.getMessage(), e);
        }

        // Step 2: Create/Update Routes
        List<RouteSpec> routeSpecs = revision.getRouteSpecifications();
        for (int i = 0; i < routeSpecs.size(); i++) {
            RouteSpec routeSpec = routeSpecs.get(i);
            String routeName = routeSpec.getName() != null ? routeSpec.getName() : "route-" + i;
            String routeId = generateRouteId(service.getOrgId(), environment.getId(),
                    service.getName(), routeName, i);

            Map<String, Object> routePayload = buildRoutePayload(routeSpec);

            try {
                log.info("Creating route {} with payload: {}", routeId, routePayload);
                String response = webClient.put()
                        .uri("/apisix/admin/routes/{id}", routeId)
                        .bodyValue(routePayload)
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                resp -> resp.bodyToMono(String.class)
                                        .flatMap(body -> {
                                            log.error("APISIX route creation failed: {} {}", resp.statusCode(), body);
                                            return Mono.error(new RuntimeException("APISIX returned " + resp.statusCode() + ": " + body));
                                        }))
                        .bodyToMono(String.class)
                        .block();
                log.info("APISIX route response: {}", response);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create route '" + routeName + "' in APISIX: " + e.getMessage(), e);
            }
        }

        log.info("Successfully deployed service and {} routes to APISIX", routeSpecs.size());
    }

    /**
     * Undeploy service and routes from an APISIX environment.
     */
    public void undeployServiceAndRoutes(Environment environment, ServiceRevision revision,
                                         Service service) {
        log.info("Undeploying service '{}' (Rev {}) from APISIX at {}",
                service.getName(), revision.getRevisionNumber(), environment.getApisixAdminUrl());

        WebClient webClient = buildWebClient(environment);
        String serviceId = service.getId();

        // Delete routes first
        List<RouteSpec> routeSpecs = revision.getRouteSpecifications();
        int deletedRoutes = 0;
        for (int i = 0; i < routeSpecs.size(); i++) {
            RouteSpec routeSpec = routeSpecs.get(i);
            String routeName = routeSpec.getName() != null ? routeSpec.getName() : "route-" + i;
            String routeId = generateRouteId(service.getOrgId(), environment.getId(),
                    service.getName(), routeName, i);

            if (tryDeleteRoute(webClient, routeId, routeName)) {
                deletedRoutes++;
            }
        }

        log.info("Deleted {}/{} routes", deletedRoutes, routeSpecs.size());

        if (deletedRoutes > 0) {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        if (!tryDeleteService(webClient, serviceId)) {
            throw new RuntimeException("Failed to delete service from APISIX: " + serviceId);
        }

        log.info("Successfully undeployed service and routes from APISIX");
    }

    /**
     * Build APISIX service payload from the stored ServiceSpec.
     * Sets upstream_id and adds a description fallback.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildServicePayload(String upstreamId, ServiceRevision revision, Service service) {
        Map<String, Object> payload;

        if (revision.getServiceSpecification() != null) {
            // Clone the spec so we don't mutate the stored object
            payload = objectMapper.convertValue(revision.getServiceSpecification(), LinkedHashMap.class);
        } else {
            payload = new LinkedHashMap<>();
        }

        // Always set upstream_id (deployment-time field)
        payload.put("upstream_id", upstreamId);

        // Default description if not set
        if (!payload.containsKey("desc") || payload.get("desc") == null) {
            payload.put("desc", String.format("%s - Revision %d", service.getName(), revision.getRevisionNumber()));
        }

        return payload;
    }

    /**
     * Build APISIX route payload from a RouteSpec.
     * service_id is already stamped on the spec at revision creation time.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRoutePayload(RouteSpec routeSpec) {
        Map<String, Object> payload = objectMapper.convertValue(routeSpec, LinkedHashMap.class);

        // Default status to enabled if not set
        if (!payload.containsKey("status") || payload.get("status") == null) {
            payload.put("status", 1);
        }

        return payload;
    }

    private String generateRouteId(String orgId, String envId, String serviceName, String routeName, int index) {
        String fullId = String.format("%s-%s-%s-%d", orgId, serviceName, routeName, index);
        String hash = Integer.toHexString(fullId.hashCode());

        String sanitized = routeName.replaceAll("[^a-zA-Z0-9-_]", "-").toLowerCase();
        sanitized = sanitized.replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (sanitized.length() > 20) {
            sanitized = sanitized.substring(0, 20);
        }

        String routeId = String.format("cp-rt-%s-%s-%d", hash, sanitized, index);
        if (routeId.length() > 64) {
            routeId = routeId.substring(0, 64);
        }
        routeId = routeId.replaceAll("-$", "");

        return routeId;
    }

    private WebClient buildWebClient(Environment environment) {
        return webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();
    }

    private boolean tryDeleteRoute(WebClient webClient, String routeId, String routeName) {
        try {
            webClient.delete()
                    .uri("/apisix/admin/routes/{id}", routeId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, resp -> Mono.empty())
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException("APISIX: " + resp.statusCode() + ": " + body))))
                    .bodyToMono(String.class)
                    .block();
            return true;
        } catch (Exception e) {
            log.error("Failed to delete route '{}' ({}): {}", routeName, routeId, e.getMessage());
            return false;
        }
    }

    private boolean tryDeleteService(WebClient webClient, String serviceId) {
        try {
            webClient.delete()
                    .uri("/apisix/admin/services/{id}", serviceId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, resp -> Mono.empty())
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException("APISIX: " + resp.statusCode() + ": " + body))))
                    .bodyToMono(String.class)
                    .block();
            return true;
        } catch (Exception e) {
            log.error("Failed to delete service {}: {}", serviceId, e.getMessage());
            return false;
        }
    }
}
