package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateSubscriptionRequest;
import com.apisix.controlplane.entity.APISubscription;
import com.apisix.controlplane.entity.ApiRevision;
import com.apisix.controlplane.entity.Developer;
import com.apisix.controlplane.entity.Environment;
import com.apisix.controlplane.enums.RevisionState;
import com.apisix.controlplane.enums.SubscriptionStatus;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.APISubscriptionRepository;
import com.apisix.controlplane.repository.ApiRevisionRepository;
import com.apisix.controlplane.repository.EnvironmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class APISubscriptionService {
    
    private final APISubscriptionRepository subscriptionRepository;
    private final DeveloperService developerService;
    private final EnvironmentRepository environmentRepository;
    private final ApiRevisionRepository apiRevisionRepository;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${apisix.admin.key}")
    private String adminKey;
    
    private static final String CONSUMER_PREFIX = "svc";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    @Transactional
    public APISubscription createSubscription(String orgId, CreateSubscriptionRequest request) {
        log.info("Creating subscription for developer {} to API {} in environment {} (org: {})", 
                request.getDeveloperId(), request.getApiName(), request.getEnvId(), orgId);
        
        // Validate developer
        Developer developer = developerService.getDeveloperById(orgId, request.getDeveloperId());
        
        // Validate environment
        Environment environment = environmentRepository.findById(request.getEnvId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + request.getEnvId()));
        
        if (!environment.getOrgId().equals(orgId)) {
            throw new BusinessException("Environment does not belong to this organization");
        }
        
        // Check if ANY subscription exists (due to unique constraint on orgId+developerId+apiName+envId)
        Optional<APISubscription> existingSubscription = subscriptionRepository
                .findByOrgIdAndDeveloperIdAndApiNameAndEnvId(
                        orgId, request.getDeveloperId(), request.getApiName(), request.getEnvId());
        
        if (existingSubscription.isPresent()) {
            APISubscription subscription = existingSubscription.get();
            
            if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                throw new BusinessException("Active subscription already exists for this developer, API, and environment");
            }
            
            if (subscription.getStatus() == SubscriptionStatus.PENDING) {
                throw new BusinessException("Pending subscription already exists for this developer, API, and environment");
            }
            
            // If REVOKED, reactivate it (grant access again)
            if (subscription.getStatus() == SubscriptionStatus.REVOKED) {
                log.info("Reactivating revoked subscription {} for developer {} to API {} in environment {}", 
                        subscription.getId(), request.getDeveloperId(), request.getApiName(), request.getEnvId());
                return grantSubscription(orgId, subscription.getId());
            }
        }
        
        // Find deployed API revision in this environment
        List<ApiRevision> revisions = apiRevisionRepository.findByOrgIdAndName(orgId, request.getApiName());
        if (revisions.isEmpty()) {
            throw new ResourceNotFoundException("API not found: " + request.getApiName());
        }
        
        // Find the deployed revision in this environment
        ApiRevision deployedRevision = revisions.stream()
                .filter(rev -> {
                    if (rev.getEnvironments() != null && rev.getEnvironments().containsKey(request.getEnvId())) {
                        return rev.getEnvironments().get(request.getEnvId()).getStatus() == RevisionState.DEPLOYED;
                    }
                    return false;
                })
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "No deployed revision found for API " + request.getApiName() + " in environment " + request.getEnvId()));
        
        // Generate service ID (same pattern as deployment)
        String serviceId = generateServiceId(orgId, request.getEnvId(), request.getApiName());
        
        // Generate or get APISIX consumer ID
        String apisixConsumerId = generateConsumerId(orgId, developer.getEmail(), request.getEnvId());
        
        // Get or generate API key - MUST reuse existing key if developer has other subscriptions
        String apiKey;
        List<APISubscription> existingSubscriptions = subscriptionRepository
                .findByOrgIdAndDeveloperIdAndEnvIdAndStatus(orgId, request.getDeveloperId(), 
                        request.getEnvId(), SubscriptionStatus.ACTIVE);
        
        if (!existingSubscriptions.isEmpty()) {
            // Reuse existing API key from any active subscription
            apiKey = existingSubscriptions.get(0).getApiKey();
            log.info("Reusing existing API key for developer {} in environment {}", 
                    request.getDeveloperId(), request.getEnvId());
        } else {
            // Generate new API key for first subscription
            apiKey = generateApiKey();
            log.info("Generated new API key for developer {} in environment {}", 
                    request.getDeveloperId(), request.getEnvId());
        }
        
        // Create subscription record
        APISubscription subscription = APISubscription.builder()
                .orgId(orgId)
                .envId(request.getEnvId())
                .developerId(request.getDeveloperId())
                .apisixConsumerId(apisixConsumerId)
                .apiName(request.getApiName())
                .serviceId(serviceId)
                .status(SubscriptionStatus.PENDING)
                .apiKey(apiKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // Create/Update consumer in APISIX with service whitelist
        try {
            createOrUpdateConsumerInApisix(environment, apisixConsumerId, apiKey, orgId, request.getDeveloperId(), serviceId);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        } catch (Exception e) {
            log.error("Failed to create/update consumer in APISIX", e);
            throw new BusinessException("Failed to create subscription in APISIX: " + e.getMessage());
        }
        
        APISubscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription created successfully with ID: {}", saved.getId());
        
        return saved;
    }
    
    public List<APISubscription> getSubscriptionsByOrganization(String orgId, String developerId, String envId) {
        log.info("Fetching subscriptions for org {} (developerId: {}, envId: {})", orgId, developerId, envId);
        
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
    
    public List<APISubscription> getSubscriptionsByDeveloper(String orgId, String developerId, String envId) {
        log.info("Fetching subscriptions for developer {} in org {} (envId: {})", developerId, orgId, envId);
        
        // Validate developer exists
        developerService.getDeveloperById(orgId, developerId);
        
        if (envId != null && !envId.isEmpty()) {
            return subscriptionRepository.findByOrgIdAndDeveloperIdAndEnvId(orgId, developerId, envId);
        } else {
            return subscriptionRepository.findByOrgIdAndDeveloperId(orgId, developerId);
        }
    }
    
    public List<APISubscription> getAllSubscriptions(String orgId) {
        log.info("Fetching all subscriptions for organization: {}", orgId);
        return subscriptionRepository.findByOrgId(orgId);
    }
    
    @Transactional
    public APISubscription grantSubscription(String orgId, String subscriptionId) {
        log.info("Granting subscription {} in organization {}", subscriptionId, orgId);
        
        APISubscription subscription = subscriptionRepository.findById(subscriptionId)
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
        
        // Validate API is still deployed in the environment
        List<ApiRevision> revisions = apiRevisionRepository.findByOrgIdAndName(orgId, subscription.getApiName());
        if (revisions.isEmpty()) {
            throw new ResourceNotFoundException("API not found: " + subscription.getApiName());
        }
        
        boolean isDeployed = revisions.stream()
                .anyMatch(rev -> {
                    if (rev.getEnvironments() != null && rev.getEnvironments().containsKey(subscription.getEnvId())) {
                        return rev.getEnvironments().get(subscription.getEnvId()).getStatus() == RevisionState.DEPLOYED;
                    }
                    return false;
                });
        
        if (!isDeployed) {
            throw new BusinessException("API " + subscription.getApiName() + 
                    " is not deployed in environment " + subscription.getEnvId());
        }
        
        // Get environment
        Environment environment = environmentRepository.findById(subscription.getEnvId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + subscription.getEnvId()));
        
        // Update status to ACTIVE FIRST (before updating APISIX)
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
        
        // Update service whitelist in APISIX consumer to add this service
        try {
            createOrUpdateConsumerInApisix(environment, subscription.getApisixConsumerId(),
                    subscription.getApiKey(), orgId, subscription.getDeveloperId(), subscription.getServiceId());
            
            log.info("Subscription granted successfully: {}", subscriptionId);
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
        log.info("Revoking subscription {} in organization {}", subscriptionId, orgId);
        
        APISubscription subscription = subscriptionRepository.findById(subscriptionId)
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
        
        // Update status to REVOKED FIRST (before updating APISIX)
        // This ensures the query in updateConsumerServiceWhitelist won't include this subscription
        subscription.setStatus(SubscriptionStatus.REVOKED);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
        
        // Update service whitelist in APISIX consumer
        try {
            updateConsumerServiceWhitelist(environment, subscription.getApisixConsumerId(), 
                    orgId, subscription.getDeveloperId(), subscription.getServiceId(), true);
            
            log.info("Subscription revoked successfully: {}", subscriptionId);
        } catch (Exception e) {
            // Rollback: Set status back to ACTIVE if APISIX update fails
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
            
            log.error("Failed to revoke subscription in APISIX", e);
            throw new BusinessException("Failed to revoke subscription: " + e.getMessage());
        }
    }
    
    private void createOrUpdateConsumerInApisix(Environment environment, String consumerId, 
                                                String apiKey, String orgId, String developerId, String newServiceId) {
        log.info("Creating/updating consumer {} in APISIX at {}", consumerId, environment.getApisixAdminUrl());
        
        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();
        
        // Get all active subscriptions for this developer in this environment
        List<APISubscription> activeSubscriptions = subscriptionRepository
                .findByOrgIdAndDeveloperIdAndEnvIdAndStatus(orgId, developerId, environment.getId(), SubscriptionStatus.ACTIVE);
        
        // Build service whitelist - include existing active subscriptions + the new one being created
        List<String> serviceWhitelist = activeSubscriptions.stream()
                .map(APISubscription::getServiceId)
                .distinct()
                .collect(Collectors.toList());
        
        // Add the new service ID if not already in the list
        if (newServiceId != null && !serviceWhitelist.contains(newServiceId)) {
            serviceWhitelist.add(newServiceId);
        }
        
        Map<String, Object> consumerPayload = new HashMap<>();
        consumerPayload.put("username", consumerId);
        consumerPayload.put("desc", "Developer " + developerId + " in org " + orgId);
        
        // Add key-auth plugin for API key authentication
        Map<String, Object> plugins = new HashMap<>();
        Map<String, Object> keyAuth = new HashMap<>();
        keyAuth.put("key", apiKey);
        plugins.put("key-auth", keyAuth);
        
        // Add consumer-restriction plugin for service whitelist
        if (!serviceWhitelist.isEmpty()) {
            Map<String, Object> consumerRestriction = new HashMap<>();
            consumerRestriction.put("whitelist", serviceWhitelist);
            consumerRestriction.put("type", "service_id");
            plugins.put("consumer-restriction", consumerRestriction);
        }
        
        consumerPayload.put("plugins", plugins);
        
        try {
            String response = webClient.put()
                    .uri("/apisix/admin/consumers/{id}", consumerId)
                    .bodyValue(consumerPayload)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("APISIX consumer creation failed with status {}: {}", resp.statusCode(), body);
                                        return Mono.error(new RuntimeException("APISIX returned " + resp.statusCode() + ": " + body));
                                    }))
                    .bodyToMono(String.class)
                    .block();
            
            log.info("APISIX consumer response: {}", response);
        } catch (Exception e) {
            log.error("Failed to create/update consumer in APISIX", e);
            throw new RuntimeException("Failed to create/update consumer in APISIX: " + e.getMessage(), e);
        }
    }
    
    private void updateConsumerServiceWhitelist(Environment environment, String consumerId, 
                                               String orgId, String developerId, 
                                               String serviceIdToRemove, boolean remove) {
        log.info("Updating service whitelist for consumer {} (remove: {})", consumerId, remove);
        
        // Get all active subscriptions except the one being revoked
        List<APISubscription> activeSubscriptions = subscriptionRepository
                .findByOrgIdAndDeveloperIdAndEnvIdAndStatus(orgId, developerId, environment.getId(), SubscriptionStatus.ACTIVE)
                .stream()
                .filter(sub -> !sub.getServiceId().equals(serviceIdToRemove))
                .collect(Collectors.toList());
        
        if (activeSubscriptions.isEmpty()) {
            // No more active subscriptions, delete the consumer
            deleteConsumerFromApisix(environment, consumerId);
        } else {
            // Update consumer with new whitelist (no new service being added, pass null)
            String apiKey = activeSubscriptions.get(0).getApiKey(); // All subscriptions share the same API key
            createOrUpdateConsumerInApisix(environment, consumerId, apiKey, orgId, developerId, null);
        }
    }
    
    private void deleteConsumerFromApisix(Environment environment, String consumerId) {
        log.info("Deleting consumer {} from APISIX", consumerId);
        
        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();
        
        try {
            webClient.delete()
                    .uri("/apisix/admin/consumers/{id}", consumerId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, 
                            resp -> Mono.empty()) // Ignore 404
                    .bodyToMono(String.class)
                    .block();
            
            log.info("Consumer deleted from APISIX: {}", consumerId);
        } catch (Exception e) {
            log.warn("Failed to delete consumer from APISIX (may not exist): {}", e.getMessage());
        }
    }
    
    private String generateConsumerId(String orgId, String email, String envId) {
        // Format: svc-{orgId}-{envId}-{hash(email)}
        String emailHash = hashString(email).substring(0, 8);
        return String.format("%s-%s-%s-%s", CONSUMER_PREFIX, orgId, envId, emailHash);
    }
    
    private String generateServiceId(String orgId, String envId, String apiName) {
        // MUST match the logic in ApisixIntegrationService.generateServiceId()
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
        
        log.debug("Generated service ID: {} for API: {}", serviceId, apiName);
        return serviceId;
    }
    
    private String generateApiKey() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
    
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

