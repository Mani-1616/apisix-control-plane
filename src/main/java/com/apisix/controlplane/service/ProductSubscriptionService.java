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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private final ProductService productService;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${apisix.admin.key}")
    private String adminKey;
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    @Transactional
    public ProductSubscription createSubscription(String orgId, CreateProductSubscriptionRequest request) {
        log.info("Creating product subscription for developer {} to product {} in environment {} (org: {})",
                request.getDeveloperId(), request.getProductId(), request.getEnvId(), orgId);
        
        // Validate developer
        Developer developer = developerService.getDeveloperById(orgId, request.getDeveloperId());
        
        // Validate environment
        Environment environment = environmentRepository.findById(request.getEnvId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + request.getEnvId()));
        
        if (!environment.getOrgId().equals(orgId)) {
            throw new BusinessException("Environment does not belong to this organization");
        }
        
        // Validate product
        Product product = productRepository.findByOrgIdAndId(orgId, request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        
        // Check if subscription already exists
        Optional<ProductSubscription> existingSubscription = subscriptionRepository
                .findByOrgIdAndDeveloperIdAndProductIdAndEnvId(
                        orgId, request.getDeveloperId(), request.getProductId(), request.getEnvId());
        
        if (existingSubscription.isPresent()) {
            ProductSubscription subscription = existingSubscription.get();
            
            if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                throw new BusinessException("Active subscription already exists for this developer, product, and environment");
            }
            
            if (subscription.getStatus() == SubscriptionStatus.PENDING) {
                throw new BusinessException("Pending subscription already exists for this developer, product, and environment");
            }
            
            // If REVOKED, reactivate it
            if (subscription.getStatus() == SubscriptionStatus.REVOKED) {
                log.info("Reactivating revoked subscription {} for developer {} to product {} in environment {}",
                        subscription.getId(), request.getDeveloperId(), request.getProductId(), request.getEnvId());
                return grantSubscription(orgId, subscription.getId());
            }
        }
        
        // Generate consumer ID and consumer group ID
        String consumerId = generateConsumerId(product.getName(), developer.getEmail(), environment.getId());
        String consumerGroupId = productService.generateConsumerGroupId(product.getName(), environment.getId());
        
        // Generate API key
        String apiKey = generateApiKey();
        
        // Create subscription record
        ProductSubscription subscription = ProductSubscription.builder()
                .orgId(orgId)
                .envId(request.getEnvId())
                .developerId(request.getDeveloperId())
                .productId(request.getProductId())
                .consumerId(consumerId)
                .consumerGroupId(consumerGroupId)
                .apiKey(apiKey)
                .status(SubscriptionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // Create consumer in APISIX and add to consumer group
        try {
            createConsumerInApisix(environment, consumerId, consumerGroupId, apiKey, orgId, developer.getId());
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        } catch (Exception e) {
            log.error("Failed to create consumer in APISIX", e);
            throw new BusinessException("Failed to create subscription in APISIX: " + e.getMessage());
        }
        
        ProductSubscription saved = subscriptionRepository.save(subscription);
        log.info("Product subscription created successfully with ID: {}", saved.getId());
        
        return saved;
    }
    
    public List<ProductSubscription> getSubscriptionsByOrganization(String orgId, String developerId, String envId) {
        log.info("Fetching product subscriptions for org {} (developerId: {}, envId: {})", orgId, developerId, envId);
        
        // Case 1: Both developer and environment filters
        if (developerId != null && !developerId.isEmpty() && envId != null && !envId.isEmpty()) {
            developerService.getDeveloperById(orgId, developerId); // Validate developer exists
            return subscriptionRepository.findByOrgIdAndDeveloperIdAndEnvId(orgId, developerId, envId);
        }
        
        // Case 2: Only developer filter
        if (developerId != null && !developerId.isEmpty()) {
            developerService.getDeveloperById(orgId, developerId); // Validate developer exists
            return subscriptionRepository.findByOrgIdAndDeveloperId(orgId, developerId);
        }
        
        // Case 3: Only environment filter
        if (envId != null && !envId.isEmpty()) {
            return subscriptionRepository.findByOrgIdAndEnvId(orgId, envId);
        }
        
        // Case 4: No filters - return all subscriptions for org
        return subscriptionRepository.findByOrgId(orgId);
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
        
        // Validate product exists
        productRepository.findByOrgIdAndId(orgId, subscription.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + subscription.getProductId()));
        
        // Get environment
        Environment environment = environmentRepository.findById(subscription.getEnvId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + subscription.getEnvId()));
        
        // Update status to ACTIVE FIRST
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
        
        // Create consumer in APISIX
        try {
            createConsumerInApisix(environment, subscription.getConsumerId(), 
                    subscription.getConsumerGroupId(), subscription.getApiKey(), 
                    orgId, subscription.getDeveloperId());
            
            log.info("Product subscription granted successfully: {}", subscriptionId);
            return subscription;
        } catch (Exception e) {
            // Rollback: Set status back to REVOKED if APISIX update fails
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
        
        // Get environment
        Environment environment = environmentRepository.findById(subscription.getEnvId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + subscription.getEnvId()));
        
        // Update status to REVOKED FIRST
        subscription.setStatus(SubscriptionStatus.REVOKED);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
        
        // Delete consumer from APISIX
        try {
            deleteConsumerFromApisix(environment, subscription.getConsumerId());
            log.info("Product subscription revoked successfully: {}", subscriptionId);
        } catch (Exception e) {
            // Rollback: Set status back to ACTIVE if APISIX update fails
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
            
            log.error("Failed to revoke subscription in APISIX", e);
            throw new BusinessException("Failed to revoke subscription: " + e.getMessage());
        }
    }
    
    /**
     * Create consumer in APISIX and add to consumer group
     */
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
        consumerPayload.put("group_id", consumerGroupId);  // Assign to consumer group
        
        // Add key-auth plugin for API key authentication
        Map<String, Object> plugins = new HashMap<>();
        Map<String, Object> keyAuth = new HashMap<>();
        keyAuth.put("key", apiKey);
        plugins.put("key-auth", keyAuth);
        
        consumerPayload.put("plugins", plugins);
        
        try {
            String response = webClient.put()
                    .uri("/apisix/admin/consumers/{id}", consumerId)
                    .bodyValue(consumerPayload)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("APISIX consumer creation failed with status {}: {}", 
                                                resp.statusCode(), body);
                                        return Mono.error(new RuntimeException("APISIX returned " + resp.statusCode() + ": " + body));
                                    }))
                    .bodyToMono(String.class)
                    .block();
            
            log.info("APISIX consumer response: {}", response);
        } catch (Exception e) {
            log.error("Failed to create consumer in APISIX", e);
            throw new RuntimeException("Failed to create consumer in APISIX: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete consumer from APISIX
     */
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
                                    log.warn("Consumer {} not found in APISIX, skipping deletion.", consumerId);
                                    return Mono.empty();
                                }
                                return resp.bodyToMono(String.class)
                                        .flatMap(body -> {
                                            log.error("APISIX consumer deletion failed with status {}: {}", 
                                                    resp.statusCode(), body);
                                            return Mono.error(new RuntimeException("APISIX returned " + resp.statusCode() + ": " + body));
                                        });
                            })
                    .bodyToMono(String.class)
                    .block();
            
            log.info("Consumer deleted from APISIX: {}", consumerId);
        } catch (Exception e) {
            log.warn("Failed to delete consumer from APISIX: {}", e.getMessage());
        }
    }
    
    /**
     * Generate consumer ID for product subscription
     * Format: prod-{productName}-{envHash}-{developerHash}
     */
    private String generateConsumerId(String productName, String developerEmail, String envId) {
        String envHash = hashString(envId).substring(0, 8);
        String devHash = hashString(developerEmail).substring(0, 8);
        
        String sanitizedName = productName.replaceAll("[^a-zA-Z0-9-_]", "-").toLowerCase();
        sanitizedName = sanitizedName.replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (sanitizedName.length() > 20) {
            sanitizedName = sanitizedName.substring(0, 20);
        }
        
        return String.format("prod-%s-%s-%s", sanitizedName, envHash, devHash);
    }
    
    /**
     * Generate unique API key
     */
    private String generateApiKey() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
    
    /**
     * Hash string using SHA-256
     */
    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash string", e);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}

