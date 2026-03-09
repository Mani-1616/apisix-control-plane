package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateProductSubscriptionRequest;
import com.apisix.controlplane.entity.Developer;
import com.apisix.controlplane.entity.Environment;
import com.apisix.controlplane.entity.Product;
import com.apisix.controlplane.entity.ProductSubscription;
import com.apisix.controlplane.enums.SubscriptionStatus;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.EnvironmentRepository;
import com.apisix.controlplane.repository.ProductRepository;
import com.apisix.controlplane.repository.ProductSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSubscriptionService {

    private final ProductSubscriptionRepository subscriptionRepository;
    private final ProductRepository productRepository;
    private final DeveloperService developerService;
    private final EnvironmentRepository environmentRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${apisix.admin.key}")
    private String adminKey;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public ProductSubscription createSubscription(String orgId, String envId, String productId,
                                                  CreateProductSubscriptionRequest request) {
        log.info("Creating product subscription for developer {} to product {} in env {} (org: {})",
                request.getDeveloperId(), productId, envId, orgId);

        Developer developer = developerService.getDeveloperById(orgId, request.getDeveloperId());

        Product product = productRepository.findByOrgIdAndId(orgId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        Environment environment = environmentRepository.findById(envId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + envId));

        Optional<ProductSubscription> existingSubscription = subscriptionRepository
                .findByOrgIdAndDeveloperIdAndProductIdAndEnvId(
                        orgId, request.getDeveloperId(), productId, envId);

        if (existingSubscription.isPresent()) {
            ProductSubscription subscription = existingSubscription.get();

            if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                throw new BusinessException("Active subscription already exists for this developer and product");
            }

            if (subscription.getStatus() == SubscriptionStatus.PENDING) {
                throw new BusinessException("Pending subscription already exists for this developer and product");
            }

            if (subscription.getStatus() == SubscriptionStatus.REVOKED) {
                log.info("Reactivating revoked subscription {}", subscription.getId());
                return grantSubscription(orgId, subscription.getId());
            }
        }

        String apiKey = generateApiKey();

        ProductSubscription subscription = ProductSubscription.builder()
                .orgId(orgId)
                .envId(envId)
                .developerId(request.getDeveloperId())
                .productId(productId)
                .apiKey(apiKey)
                .status(SubscriptionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProductSubscription saved = subscriptionRepository.save(subscription);

        try {
            createConsumerInApisix(environment, saved.getId(), product.getId(), apiKey, orgId, developer.getId());
            saved.setStatus(SubscriptionStatus.ACTIVE);
            saved.setUpdatedAt(LocalDateTime.now());
            saved = subscriptionRepository.save(saved);
        } catch (Exception e) {
            log.error("Failed to create consumer in APISIX", e);
            throw new BusinessException("Failed to create subscription in APISIX: " + e.getMessage());
        }

        log.info("Product subscription created successfully with ID: {}", saved.getId());
        return saved;
    }

    public List<ProductSubscription> getSubscriptionsByOrganization(String orgId, String envId, String developerId) {
        log.info("Fetching product subscriptions for org {} env {} (developerId: {})", orgId, envId, developerId);

        if (developerId != null && !developerId.isEmpty()) {
            developerService.getDeveloperById(orgId, developerId);
            return subscriptionRepository.findByOrgIdAndDeveloperIdAndEnvId(orgId, developerId, envId);
        }

        return subscriptionRepository.findByOrgIdAndEnvId(orgId, envId);
    }

    @Transactional
    public ProductSubscription grantSubscription(String orgId, String subscriptionId) {
        log.info("Granting product subscription {} in organization {}", subscriptionId, orgId);

        ProductSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        if (!subscription.getOrgId().equals(orgId)) {
            throw new BusinessException("Subscription does not belong to this organization");
        }

        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            throw new BusinessException("Subscription is already active");
        }

        if (subscription.getStatus() == SubscriptionStatus.PENDING) {
            throw new BusinessException("Subscription is pending");
        }

        Product product = productRepository.findByOrgIdAndId(orgId, subscription.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + subscription.getProductId()));

        Environment environment = environmentRepository.findById(subscription.getEnvId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + subscription.getEnvId()));

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        try {
            createConsumerInApisix(environment, subscription.getId(), product.getId(),
                    subscription.getApiKey(), orgId, subscription.getDeveloperId());

            log.info("Product subscription granted successfully: {}", subscriptionId);
            return subscription;
        } catch (Exception e) {
            subscription.setStatus(SubscriptionStatus.REVOKED);
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);

            log.error("Failed to grant subscription in APISIX", e);
            throw new BusinessException("Failed to grant subscription: " + e.getMessage());
        }
    }

    @Transactional
    public void revokeSubscription(String orgId, String subscriptionId) {
        log.info("Revoking product subscription {} in organization {}", subscriptionId, orgId);

        ProductSubscription subscription = subscriptionRepository.findById(subscriptionId)
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
            deleteConsumerFromApisix(environment, subscription.getId());
            log.info("Product subscription revoked successfully: {}", subscriptionId);
        } catch (Exception e) {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);

            log.error("Failed to revoke subscription in APISIX", e);
            throw new BusinessException("Failed to revoke subscription: " + e.getMessage());
        }
    }

    private void createConsumerInApisix(Environment environment, String consumerId,
                                       String consumerGroupId, String apiKey,
                                       String orgId, String developerId) {
        log.info("Creating consumer {} in APISIX at {}", consumerId, environment.getApisixAdminUrl());

        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();

        Map<String, Object> consumerPayload = new HashMap<>();
        consumerPayload.put("username", consumerId);
        consumerPayload.put("desc", "Developer " + developerId + " in org " + orgId);
        consumerPayload.put("group_id", consumerGroupId);

        Map<String, Object> plugins = new HashMap<>();
        Map<String, Object> keyAuth = new HashMap<>();
        keyAuth.put("key", apiKey);
        plugins.put("key-auth", keyAuth);

        consumerPayload.put("plugins", plugins);

        webClient.put()
                .uri("/apisix/admin/consumers/{id}", consumerId)
                .bodyValue(consumerPayload)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException("APISIX: " + resp.statusCode() + ": " + body))))
                .bodyToMono(String.class)
                .block();
    }

    private void deleteConsumerFromApisix(Environment environment, String consumerId) {
        log.info("Deleting consumer {} from APISIX at {}", consumerId, environment.getApisixAdminUrl());

        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();

        try {
            webClient.delete()
                    .uri("/apisix/admin/consumers/{id}", consumerId)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(),
                            resp -> {
                                if (resp.statusCode() == HttpStatus.NOT_FOUND) {
                                    return Mono.empty();
                                }
                                return resp.bodyToMono(String.class)
                                        .flatMap(body -> Mono.error(new RuntimeException("APISIX: " + resp.statusCode() + ": " + body)));
                            })
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
