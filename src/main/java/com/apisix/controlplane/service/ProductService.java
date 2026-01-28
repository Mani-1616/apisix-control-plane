package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateProductRequest;
import com.apisix.controlplane.entity.ApiRevision;
import com.apisix.controlplane.entity.Environment;
import com.apisix.controlplane.entity.Product;
import com.apisix.controlplane.entity.ProductSubscription;
import com.apisix.controlplane.enums.RevisionState;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.ApiRevisionRepository;
import com.apisix.controlplane.repository.EnvironmentRepository;
import com.apisix.controlplane.repository.OrganizationRepository;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    
    private final ProductRepository productRepository;
    private final ProductSubscriptionRepository productSubscriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final EnvironmentRepository environmentRepository;
    private final ApiRevisionRepository apiRevisionRepository;
    private final ApisixIntegrationService apisixIntegrationService;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${apisix.admin.key}")
    private String adminKey;
    
    private static final String CONSUMER_GROUP_PREFIX = "grp";
    
    @Transactional
    public Product createProduct(String orgId, CreateProductRequest request) {
        log.info("Creating product '{}' for organization: {}", request.getName(), orgId);
        
        // Validate organization exists
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization not found: " + orgId);
        }
        
        // Check if product with same name already exists
        if (productRepository.existsByOrgIdAndName(orgId, request.getName())) {
            throw new BusinessException("Product with name '" + request.getName() + "' already exists in this organization");
        }
        
        // Validate that all APIs exist
        for (String apiName : request.getApiNames()) {
            List<ApiRevision> revisions = apiRevisionRepository.findByOrgIdAndName(orgId, apiName);
            if (revisions.isEmpty()) {
                throw new ResourceNotFoundException("API not found: " + apiName);
            }
        }
        
        // Validate plugin config if provided
        if (request.getPluginConfig() != null && !request.getPluginConfig().trim().isEmpty()) {
            validatePluginConfig(request.getPluginConfig());
        }
        
        Product product = Product.builder()
                .orgId(orgId)
                .name(request.getName())
                .description(request.getDescription())
                .displayName(request.getDisplayName())
                .apiNames(request.getApiNames())
                .pluginConfig(request.getPluginConfig())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        Product saved = productRepository.save(product);
        log.info("Product created with ID: {}", saved.getId());
        
        // Create consumer groups in APISIX immediately
        createConsumerGroupsForProduct(orgId, saved);
        
        return saved;
    }
    
    @Transactional
    public Product updateProduct(String orgId, String productId, CreateProductRequest request) {
        log.info("Updating product {} in organization {}", productId, orgId);
        
        Product product = productRepository.findByOrgIdAndId(orgId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        
        // Validate that all APIs exist
        for (String apiName : request.getApiNames()) {
            List<ApiRevision> revisions = apiRevisionRepository.findByOrgIdAndName(orgId, apiName);
            if (revisions.isEmpty()) {
                throw new ResourceNotFoundException("API not found: " + apiName);
            }
        }
        
        // Validate plugin config if provided
        if (request.getPluginConfig() != null && !request.getPluginConfig().trim().isEmpty()) {
            validatePluginConfig(request.getPluginConfig());
        }
        
        product.setDescription(request.getDescription());
        product.setDisplayName(request.getDisplayName());
        product.setApiNames(request.getApiNames());
        product.setPluginConfig(request.getPluginConfig());
        product.setUpdatedAt(LocalDateTime.now());
        
        // Update consumer groups in APISIX
        updateConsumerGroupsForProduct(orgId, product);
        
        Product updated = productRepository.save(product);
        log.info("Product updated: {}", productId);
        
        return updated;
    }
    
    public List<Product> getProductsByOrganization(String orgId) {
        log.info("Fetching all products for organization: {}", orgId);
        return productRepository.findByOrgId(orgId);
    }
    
    public Product getProductById(String orgId, String productId) {
        log.info("Fetching product {} in organization {}", productId, orgId);
        return productRepository.findByOrgIdAndId(orgId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }
    
    @Transactional
    public void deleteProduct(String orgId, String productId, boolean force) {
        log.info("Deleting product {} from organization {} (force: {})", productId, orgId, force);
        
        Product product = productRepository.findByOrgIdAndId(orgId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        
        // Check for existing subscriptions
        List<ProductSubscription> subscriptions = productSubscriptionRepository.findByOrgIdAndProductId(orgId, productId);
        
        if (!subscriptions.isEmpty() && !force) {
            throw new BusinessException(
                "Cannot delete product '" + product.getDisplayName() + "'. " +
                subscriptions.size() + " active subscription(s) exist. " +
                "Use force delete to remove all subscriptions and delete the product."
            );
        }
        
        // If force delete, remove all subscriptions from APISIX and MongoDB
        if (force && !subscriptions.isEmpty()) {
            log.info("Force delete: Removing {} subscriptions", subscriptions.size());
            
            for (ProductSubscription subscription : subscriptions) {
                try {
                    // Get environment
                    Environment environment = environmentRepository.findById(subscription.getEnvId())
                            .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + subscription.getEnvId()));
                    
                    // Delete consumer from APISIX
                    deleteConsumerFromApisix(environment, subscription.getConsumerId());
                    
                    // Delete subscription from MongoDB
                    productSubscriptionRepository.delete(subscription);
                    log.info("Deleted subscription {} from APISIX and MongoDB", subscription.getId());
                } catch (Exception e) {
                    log.error("Failed to delete subscription {}: {}", subscription.getId(), e.getMessage());
                    // Continue with other subscriptions
                }
            }
        }
        
        // Delete consumer groups from all environments
        deleteConsumerGroupsForProduct(orgId, product);
        
        // Delete product from MongoDB
        productRepository.delete(product);
        log.info("Product deleted: {}", productId);
    }
    
    /**
     * Create consumer groups in all environments for this product
     */
    private void createConsumerGroupsForProduct(String orgId, Product product) {
        log.info("Creating consumer groups for product: {}", product.getName());
        
        List<Environment> environments = environmentRepository.findByOrgId(orgId);
        
        for (Environment environment : environments) {
            try {
                String consumerGroupId = generateConsumerGroupId(product.getName(), environment.getId());
                List<String> serviceIds = getDeployedServiceIdsForProduct(orgId, environment.getId(), product.getApiNames());
                createOrUpdateConsumerGroupInApisix(environment, consumerGroupId, product.getDisplayName(), serviceIds, product.getPluginConfig());
                log.info("Created consumer group {} in environment {}", consumerGroupId, environment.getName());
            } catch (Exception e) {
                log.error("Failed to create consumer group in environment {}: {}", environment.getName(), e.getMessage());
                // Continue with other environments
            }
        }
    }
    
    /**
     * Update consumer groups in all environments (when product APIs change)
     */
    private void updateConsumerGroupsForProduct(String orgId, Product product) {
        log.info("Updating consumer groups for product: {}", product.getName());
        
        List<Environment> environments = environmentRepository.findByOrgId(orgId);
        
        for (Environment environment : environments) {
            try {
                String consumerGroupId = generateConsumerGroupId(product.getName(), environment.getId());
                List<String> serviceIds = getDeployedServiceIdsForProduct(orgId, environment.getId(), product.getApiNames());
                createOrUpdateConsumerGroupInApisix(environment, consumerGroupId, product.getDisplayName(), serviceIds, product.getPluginConfig());
                log.info("Updated consumer group {} in environment {}", consumerGroupId, environment.getName());
            } catch (Exception e) {
                log.error("Failed to update consumer group in environment {}: {}", environment.getName(), e.getMessage());
            }
        }
    }
    
    /**
     * Delete consumer groups from all environments
     */
    private void deleteConsumerGroupsForProduct(String orgId, Product product) {
        log.info("Deleting consumer groups for product: {}", product.getName());
        
        List<Environment> environments = environmentRepository.findByOrgId(orgId);
        
        for (Environment environment : environments) {
            try {
                String consumerGroupId = generateConsumerGroupId(product.getName(), environment.getId());
                deleteConsumerGroupFromApisix(environment, consumerGroupId);
                log.info("Deleted consumer group {} from environment {}", consumerGroupId, environment.getName());
            } catch (Exception e) {
                log.warn("Failed to delete consumer group from environment {}: {}", environment.getName(), e.getMessage());
                // Continue with other environments
            }
        }
    }
    
    /**
     * Get deployed service IDs for all APIs in the product for a specific environment
     */
    private List<String> getDeployedServiceIdsForProduct(String orgId, String envId, List<String> apiNames) {
        return apiNames.stream()
                .map(apiName -> {
                    List<ApiRevision> revisions = apiRevisionRepository.findByOrgIdAndName(orgId, apiName);
                    Optional<ApiRevision> deployedRevision = revisions.stream()
                            .filter(rev -> rev.getEnvironments() != null &&
                                    rev.getEnvironments().containsKey(envId) &&
                                    rev.getEnvironments().get(envId).getStatus() == RevisionState.DEPLOYED)
                            .findFirst();
                    if (deployedRevision.isEmpty()) {
                        log.warn("API '{}' in product is not deployed in environment '{}', skipping from whitelist.", apiName, envId);
                        return null;
                    }
                    return apisixIntegrationService.generateServiceId(orgId, envId, apiName);
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * Create or update consumer group in APISIX
     */
    private void createOrUpdateConsumerGroupInApisix(Environment environment, String consumerGroupId, String displayName, List<String> serviceIds, String pluginConfigJson) {
        log.info("Creating/updating consumer group {} in APISIX at {}", consumerGroupId, environment.getApisixAdminUrl());
        
        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();
        
        Map<String, Object> consumerGroupPayload = new HashMap<>();
        consumerGroupPayload.put("desc", "Product: " + displayName);
        
        Map<String, Object> plugins = new HashMap<>();
        
        // Add consumer-restriction plugin for service whitelisting
        if (!serviceIds.isEmpty()) {
            Map<String, Object> consumerRestriction = new HashMap<>();
            consumerRestriction.put("whitelist", serviceIds);
            consumerRestriction.put("type", "service_id");
            plugins.put("consumer-restriction", consumerRestriction);
        }
        
        // Merge custom plugin configuration if provided
        if (pluginConfigJson != null && !pluginConfigJson.trim().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> customPlugins = mapper.readValue(pluginConfigJson, Map.class);
                plugins.putAll(customPlugins);
                log.info("Applied custom plugin configuration to consumer group {}", consumerGroupId);
            } catch (Exception e) {
                log.error("Failed to parse plugin configuration JSON: {}", e.getMessage());
                throw new BusinessException("Invalid plugin configuration JSON: " + e.getMessage());
            }
        }
        
        consumerGroupPayload.put("plugins", plugins);
        
        try {
            String response = webClient.put()
                    .uri("/apisix/admin/consumer_groups/{id}", consumerGroupId)
                    .bodyValue(consumerGroupPayload)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("APISIX consumer group creation failed with status {}: {}", resp.statusCode(), body);
                                        return Mono.error(new RuntimeException("APISIX returned " + resp.statusCode() + ": " + body));
                                    }))
                    .onStatus(status -> status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("APISIX consumer group creation failed with status {}: {}", resp.statusCode(), body);
                                        return Mono.error(new RuntimeException("APISIX returned " + resp.statusCode() + ": " + body));
                                    }))
                    .bodyToMono(String.class)
                    .block();
            
            log.info("APISIX consumer group response: {}", response);
        } catch (Exception e) {
            log.error("Failed to create/update consumer group in APISIX", e);
            throw new RuntimeException("Failed to create/update consumer group in APISIX: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete consumer group from APISIX
     */
    private void deleteConsumerGroupFromApisix(Environment environment, String consumerGroupId) {
        log.info("Deleting consumer group {} from APISIX at {}", consumerGroupId, environment.getApisixAdminUrl());
        
        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();
        
        try {
            webClient.delete()
                    .uri("/apisix/admin/consumer_groups/{id}", consumerGroupId)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(),
                            resp -> {
                                if (resp.statusCode() == HttpStatus.NOT_FOUND) {
                                    log.warn("Consumer group {} not found in APISIX, already deleted", consumerGroupId);
                                    return Mono.empty();
                                }
                                return resp.bodyToMono(String.class)
                                        .flatMap(body -> {
                                            log.error("APISIX consumer group deletion failed: {}", body);
                                            return Mono.error(new RuntimeException("APISIX returned " + resp.statusCode() + ": " + body));
                                        });
                            })
                    .bodyToMono(String.class)
                    .block();
            
            log.info("Consumer group {} deleted from APISIX", consumerGroupId);
        } catch (Exception e) {
            log.warn("Failed to delete consumer group from APISIX: {}", e.getMessage());
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
                                    log.warn("Consumer {} not found in APISIX, already deleted", consumerId);
                                    return Mono.empty();
                                }
                                return resp.bodyToMono(String.class)
                                        .flatMap(body -> {
                                            log.error("APISIX consumer deletion failed: {}", body);
                                            return Mono.error(new RuntimeException("APISIX returned " + resp.statusCode() + ": " + body));
                                        });
                            })
                    .bodyToMono(String.class)
                    .block();
            
            log.info("Consumer {} deleted from APISIX", consumerId);
        } catch (Exception e) {
            log.warn("Failed to delete consumer from APISIX: {}", e.getMessage());
        }
    }
    
    /**
     * Generate consumer group ID for a product in a specific environment
     */
    public String generateConsumerGroupId(String productName, String envId) {
        String productHash = hashString(productName).substring(0, 8);
        String envHash = Integer.toHexString(envId.hashCode());
        envHash = String.format("%8s", envHash).replace(' ', '0');
        if (envHash.length() > 8) {
            envHash = envHash.substring(0, 8);
        }
        return String.format("%s-%s-%s", CONSUMER_GROUP_PREFIX, productHash, envHash);
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
    
    /**
     * Validate plugin configuration JSON
     */
    private void validatePluginConfig(String pluginConfigJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readValue(pluginConfigJson, Map.class);
        } catch (Exception e) {
            throw new BusinessException("Invalid plugin configuration JSON: " + e.getMessage());
        }
    }
}
