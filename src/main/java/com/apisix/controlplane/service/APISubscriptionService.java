package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateSubscriptionRequest;
import com.apisix.controlplane.entity.*;
import com.apisix.controlplane.enums.SubscriptionStatus;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.APISubscriptionRepository;
import com.apisix.controlplane.repository.DeploymentRepository;
import com.apisix.controlplane.repository.EnvironmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class APISubscriptionService {

    private final APISubscriptionRepository subscriptionRepository;
    private final DeveloperService developerService;
    private final EnvironmentRepository environmentRepository;
    private final DeploymentRepository deploymentRepository;
    private final ApiService apiService;
    private final WebClient.Builder webClientBuilder;

    @Value("${apisix.admin.key}")
    private String adminKey;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public APISubscription createSubscription(String orgId, String apiId, CreateSubscriptionRequest request) {
        log.info("Creating subscription for developer {} to API {} in environment {} (org: {})",
                request.getDeveloperId(), apiId, request.getEnvId(), orgId);

        developerService.getDeveloperById(orgId, request.getDeveloperId());

        Environment environment = environmentRepository.findById(request.getEnvId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + request.getEnvId()));
        if (!environment.getOrgId().equals(orgId)) {
            throw new BusinessException("Environment does not belong to this organization");
        }

        Api api = apiService.getApiById(apiId);
        if (!api.getOrgId().equals(orgId)) {
            throw new BusinessException("API does not belong to this organization");
        }

        Optional<APISubscription> existing = subscriptionRepository
                .findByOrgIdAndDeveloperIdAndApiIdAndEnvId(
                        orgId, request.getDeveloperId(), apiId, request.getEnvId());

        if (existing.isPresent()) {
            APISubscription sub = existing.get();
            if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
                throw new BusinessException("Active subscription already exists");
            }
            if (sub.getStatus() == SubscriptionStatus.PENDING) {
                throw new BusinessException("Pending subscription already exists");
            }
            if (sub.getStatus() == SubscriptionStatus.REVOKED) {
                return grantSubscription(orgId, sub.getId());
            }
        }

        deploymentRepository.findByApiIdAndEnvironmentId(apiId, request.getEnvId())
                .orElseThrow(() -> new BusinessException(
                        "No deployed revision found for API " + api.getName() + " in environment " + request.getEnvId()));

        String apiKey;
        List<APISubscription> activeSubscriptions = subscriptionRepository
                .findByOrgIdAndDeveloperIdAndEnvIdAndStatus(orgId, request.getDeveloperId(),
                        request.getEnvId(), SubscriptionStatus.ACTIVE);

        if (!activeSubscriptions.isEmpty()) {
            apiKey = activeSubscriptions.get(0).getApiKey();
        } else {
            apiKey = generateApiKey();
        }

        APISubscription subscription = APISubscription.builder()
                .orgId(orgId)
                .envId(request.getEnvId())
                .developerId(request.getDeveloperId())
                .apiId(apiId)
                .status(SubscriptionStatus.PENDING)
                .apiKey(apiKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        try {
            createOrUpdateConsumerInApisix(environment, request.getDeveloperId(), apiKey, orgId, request.getDeveloperId(), apiId);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        } catch (Exception e) {
            throw new BusinessException("Failed to create subscription in APISIX: " + e.getMessage());
        }

        APISubscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription created with ID: {}", saved.getId());
        return saved;
    }

    public Page<APISubscription> getSubscriptions(String orgId, String developerId, String envId, Pageable pageable) {
        if (developerId != null && !developerId.isEmpty() && envId != null && !envId.isEmpty()) {
            return subscriptionRepository.findByOrgIdAndDeveloperIdAndEnvId(orgId, developerId, envId, pageable);
        }
        if (developerId != null && !developerId.isEmpty()) {
            return subscriptionRepository.findByOrgIdAndDeveloperId(orgId, developerId, pageable);
        }
        if (envId != null && !envId.isEmpty()) {
            return subscriptionRepository.findByOrgIdAndEnvId(orgId, envId, pageable);
        }
        return subscriptionRepository.findByOrgId(orgId, pageable);
    }

    public Page<APISubscription> getSubscriptionsByApi(String orgId, String apiId, Pageable pageable) {
        return subscriptionRepository.findByOrgIdAndApiId(orgId, apiId, pageable);
    }

    @Transactional
    public APISubscription grantSubscription(String orgId, String subscriptionId) {
        APISubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        if (!subscription.getOrgId().equals(orgId)) {
            throw new BusinessException("Subscription does not belong to this organization");
        }
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            throw new BusinessException("Subscription is already active");
        }

        // Validate service is still deployed
        Api api = apiService.getApiById(subscription.getApiId());
        boolean isDeployed = deploymentRepository.existsByApiIdAndEnvironmentId(
                subscription.getApiId(), subscription.getEnvId());

        if (!isDeployed) {
            throw new BusinessException("API " + api.getName() + " is not deployed in environment " + subscription.getEnvId());
        }

        Environment environment = environmentRepository.findById(subscription.getEnvId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + subscription.getEnvId()));

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        try {
            createOrUpdateConsumerInApisix(environment, subscription.getDeveloperId(),
                    subscription.getApiKey(), orgId, subscription.getDeveloperId(), subscription.getApiId());
            return subscription;
        } catch (Exception e) {
            subscription.setStatus(SubscriptionStatus.REVOKED);
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
            throw new BusinessException("Failed to grant subscription: " + e.getMessage());
        }
    }

    @Transactional
    public void revokeSubscription(String orgId, String subscriptionId) {
        APISubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        if (!subscription.getOrgId().equals(orgId)) {
            throw new BusinessException("Subscription does not belong to this organization");
        }
        if (subscription.getStatus() == SubscriptionStatus.REVOKED) {
            throw new BusinessException("Subscription is already revoked");
        }

        Environment environment = environmentRepository.findById(subscription.getEnvId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + subscription.getEnvId()));

        subscription.setStatus(SubscriptionStatus.REVOKED);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        try {
            updateConsumerServiceWhitelist(environment, subscription.getDeveloperId(),
                    orgId, subscription.getDeveloperId(), subscription.getApiId());
        } catch (Exception e) {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
            throw new BusinessException("Failed to revoke subscription: " + e.getMessage());
        }
    }

    private void createOrUpdateConsumerInApisix(Environment environment, String consumerId,
                                                String apiKey, String orgId, String developerId, String newApisixServiceId) {
        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();

        List<APISubscription> activeSubscriptions = subscriptionRepository
                .findByOrgIdAndDeveloperIdAndEnvIdAndStatus(orgId, developerId, environment.getId(), SubscriptionStatus.ACTIVE);

        List<String> serviceWhitelist = activeSubscriptions.stream()
                .map(APISubscription::getApiId)
                .distinct()
                .collect(Collectors.toList());

        if (newApisixServiceId != null && !serviceWhitelist.contains(newApisixServiceId)) {
            serviceWhitelist.add(newApisixServiceId);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("username", consumerId);
        payload.put("desc", "Developer " + developerId + " in org " + orgId);

        Map<String, Object> plugins = new HashMap<>();
        Map<String, Object> keyAuth = new HashMap<>();
        keyAuth.put("key", apiKey);
        plugins.put("key-auth", keyAuth);

        if (!serviceWhitelist.isEmpty()) {
            Map<String, Object> restriction = new HashMap<>();
            restriction.put("whitelist", serviceWhitelist);
            restriction.put("type", "service_id");
            plugins.put("consumer-restriction", restriction);
        }

        payload.put("plugins", plugins);

        webClient.put()
                .uri("/apisix/admin/consumers/{id}", consumerId)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException("APISIX: " + resp.statusCode() + ": " + body))))
                .bodyToMono(String.class)
                .block();
    }

    private void updateConsumerServiceWhitelist(Environment environment, String consumerId,
                                               String orgId, String developerId, String serviceIdToRemove) {
        List<APISubscription> activeSubscriptions = subscriptionRepository
                .findByOrgIdAndDeveloperIdAndEnvIdAndStatus(orgId, developerId, environment.getId(), SubscriptionStatus.ACTIVE)
                .stream()
                .filter(sub -> !sub.getApiId().equals(serviceIdToRemove))
                .collect(Collectors.toList());

        if (activeSubscriptions.isEmpty()) {
            deleteConsumerFromApisix(environment, consumerId);
        } else {
            String apiKey = activeSubscriptions.get(0).getApiKey();
            createOrUpdateConsumerInApisix(environment, consumerId, apiKey, orgId, developerId, null);
        }
    }

    private void deleteConsumerFromApisix(Environment environment, String consumerId) {
        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();

        try {
            webClient.delete()
                    .uri("/apisix/admin/consumers/{id}", consumerId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, resp -> Mono.empty())
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.warn("Failed to delete consumer from APISIX: {}", e.getMessage());
        }
    }

    private String generateApiKey() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

}
