package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateUpstreamRequest;
import com.apisix.controlplane.entity.Environment;
import com.apisix.controlplane.entity.Upstream;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.UpstreamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Service for managing environment-scoped upstreams.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpstreamService {

    private final UpstreamRepository upstreamRepository;
    private final EnvironmentService environmentService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${apisix.admin-key:edd1c9f034335f136f87ad84b625c8f1}")
    private String adminKey;

    @Transactional
    public Upstream createUpstream(String environmentId, CreateUpstreamRequest config) {
        log.info("Creating upstream '{}' for environment: {}", config.getName(), environmentId);

        Environment environment = environmentService.getEnvironmentById(environmentId);

        if (upstreamRepository.existsByEnvironmentIdAndName(environmentId, config.getName())) {
            throw new BusinessException("Upstream with name '" + config.getName() + "' already exists in this environment");
        }

        if (config.getSpecification() == null) {
            throw new BusinessException("Upstream specification is required");
        }

        Upstream upstream = Upstream.builder()
                .orgId(environment.getOrgId())
                .environmentId(environmentId)
                .name(config.getName())
                .specification(config.getSpecification())
                .build();

        upstream.generateApisixId();

        Upstream saved = upstreamRepository.save(upstream);
        log.info("Created upstream entity with ID: {}, APISIX ID: {}", saved.getId(), saved.getApisixId());

        // Create in APISIX immediately
        try {
            createUpstreamInApisix(environment, saved);
            upstreamRepository.save(saved);
        } catch (Exception e) {
            log.error("Failed to create upstream in APISIX", e);
            upstreamRepository.save(saved);
            throw new BusinessException("Upstream created in control plane but failed to create in APISIX: " + e.getMessage());
        }

        return saved;
    }

    private void createUpstreamInApisix(Environment environment, Upstream upstream) {
        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();

        // Serialize the spec directly as the APISIX payload
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.convertValue(upstream.getSpecification(), Map.class);

        String response = webClient.put()
                .uri("/apisix/admin/upstreams/{id}", upstream.getApisixId())
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("APISIX upstream creation failed: {} {}", resp.statusCode(), body);
                                    return Mono.error(new RuntimeException("APISIX returned " + resp.statusCode() + ": " + body));
                                }))
                .bodyToMono(String.class)
                .block();

        log.info("APISIX upstream creation response: {}", response);
    }

    public Upstream getUpstreamById(String upstreamId) {
        return upstreamRepository.findById(upstreamId)
                .orElseThrow(() -> new ResourceNotFoundException("Upstream not found with ID: " + upstreamId));
    }

    public List<Upstream> getUpstreamsByEnvironment(String environmentId) {
        return upstreamRepository.findByEnvironmentId(environmentId);
    }

    public Page<Upstream> getUpstreamsByEnvironment(String environmentId, Pageable pageable) {
        return upstreamRepository.findByEnvironmentId(environmentId, pageable);
    }

    public List<Upstream> getUpstreamsByOrg(String orgId) {
        return upstreamRepository.findByOrgId(orgId);
    }

    @Transactional
    public void deleteUpstream(String upstreamId) {
        Upstream upstream = getUpstreamById(upstreamId);

        try {
            Environment environment = environmentService.getEnvironmentById(upstream.getEnvironmentId());
            deleteUpstreamFromApisix(environment, upstream);
        } catch (Exception e) {
            log.warn("Failed to delete upstream from APISIX: {}", e.getMessage());
        }

        upstreamRepository.delete(upstream);
        log.info("Deleted upstream '{}'", upstream.getName());
    }

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
                                .flatMap(body -> Mono.error(new RuntimeException("Failed to delete: " + body))))
                .bodyToMono(String.class)
                .block();
    }
}
