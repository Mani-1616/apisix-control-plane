package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateProductRequest;
import com.apisix.controlplane.entity.*;
import com.apisix.controlplane.exception.BusinessException;
import com.apisix.controlplane.exception.ResourceNotFoundException;
import com.apisix.controlplane.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSubscriptionRepository productSubscriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final EnvironmentRepository environmentRepository;
    private final ApiServiceRepository apiServiceRepository;
    private final DeploymentRepository deploymentRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${apisix.admin.key}")
    private String adminKey;

    private static final String CONSUMER_GROUP_PREFIX = "grp";

    @Transactional
    public Product createProduct(String orgId, CreateProductRequest request) {
        log.info("Creating product '{}' for organization: {}", request.getName(), orgId);

        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization not found: " + orgId);
        }
        if (productRepository.existsByOrgIdAndName(orgId, request.getName())) {
            throw new BusinessException("Product with name '" + request.getName() + "' already exists");
        }

        // Validate all services exist and belong to this org
        for (String serviceId : request.getServiceIds()) {
            Service svc = apiServiceRepository.findById(serviceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
            if (!svc.getOrgId().equals(orgId)) {
                throw new BusinessException("Service " + serviceId + " does not belong to this organization");
            }
        }

        if (request.getPluginConfig() != null && !request.getPluginConfig().trim().isEmpty()) {
            validatePluginConfig(request.getPluginConfig());
        }

        Product product = Product.builder()
                .orgId(orgId)
                .name(request.getName())
                .description(request.getDescription())
                .displayName(request.getDisplayName())
                .serviceIds(request.getServiceIds())
                .pluginConfig(request.getPluginConfig())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Product saved = productRepository.save(product);
        log.info("Product created with ID: {}", saved.getId());

        createConsumerGroupsForProduct(orgId, saved);

        return saved;
    }

    @Transactional
    public Product updateProduct(String orgId, String productId, CreateProductRequest request) {
        Product product = productRepository.findByOrgIdAndId(orgId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        for (String serviceId : request.getServiceIds()) {
            Service svc = apiServiceRepository.findById(serviceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
            if (!svc.getOrgId().equals(orgId)) {
                throw new BusinessException("Service " + serviceId + " does not belong to this organization");
            }
        }

        if (request.getPluginConfig() != null && !request.getPluginConfig().trim().isEmpty()) {
            validatePluginConfig(request.getPluginConfig());
        }

        product.setDescription(request.getDescription());
        product.setDisplayName(request.getDisplayName());
        product.setServiceIds(request.getServiceIds());
        product.setPluginConfig(request.getPluginConfig());
        product.setUpdatedAt(LocalDateTime.now());

        updateConsumerGroupsForProduct(orgId, product);

        return productRepository.save(product);
    }

    public List<Product> getProductsByOrganization(String orgId) {
        return productRepository.findByOrgId(orgId);
    }

    public Product getProductById(String orgId, String productId) {
        return productRepository.findByOrgIdAndId(orgId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    @Transactional
    public void deleteProduct(String orgId, String productId, boolean force) {
        Product product = productRepository.findByOrgIdAndId(orgId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        List<ProductSubscription> subscriptions = productSubscriptionRepository.findByOrgIdAndProductId(orgId, productId);

        if (!subscriptions.isEmpty() && !force) {
            throw new BusinessException("Cannot delete product. " + subscriptions.size() + " subscriptions exist. Use force delete.");
        }

        if (force && !subscriptions.isEmpty()) {
            for (ProductSubscription sub : subscriptions) {
                try {
                    Environment env = environmentRepository.findById(sub.getEnvId())
                            .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + sub.getEnvId()));
                    deleteConsumerFromApisix(env, sub.getConsumerId());
                    productSubscriptionRepository.delete(sub);
                } catch (Exception e) {
                    log.error("Failed to delete subscription {}: {}", sub.getId(), e.getMessage());
                }
            }
        }

        deleteConsumerGroupsForProduct(orgId, product);
        productRepository.delete(product);
        log.info("Product deleted: {}", productId);
    }

    private void createConsumerGroupsForProduct(String orgId, Product product) {
        List<Environment> environments = environmentRepository.findByOrgId(orgId);
        for (Environment env : environments) {
            try {
                String groupId = generateConsumerGroupId(product.getName(), env.getId());
                List<String> apisixServiceIds = getDeployedApisixServiceIds(orgId, env.getId(), product.getServiceIds());
                createOrUpdateConsumerGroupInApisix(env, groupId, product.getDisplayName(), apisixServiceIds, product.getPluginConfig());
            } catch (Exception e) {
                log.error("Failed to create consumer group in env {}: {}", env.getName(), e.getMessage());
            }
        }
    }

    private void updateConsumerGroupsForProduct(String orgId, Product product) {
        List<Environment> environments = environmentRepository.findByOrgId(orgId);
        for (Environment env : environments) {
            try {
                String groupId = generateConsumerGroupId(product.getName(), env.getId());
                List<String> apisixServiceIds = getDeployedApisixServiceIds(orgId, env.getId(), product.getServiceIds());
                createOrUpdateConsumerGroupInApisix(env, groupId, product.getDisplayName(), apisixServiceIds, product.getPluginConfig());
            } catch (Exception e) {
                log.error("Failed to update consumer group in env {}: {}", env.getName(), e.getMessage());
            }
        }
    }

    private void deleteConsumerGroupsForProduct(String orgId, Product product) {
        List<Environment> environments = environmentRepository.findByOrgId(orgId);
        for (Environment env : environments) {
            try {
                String groupId = generateConsumerGroupId(product.getName(), env.getId());
                deleteConsumerGroupFromApisix(env, groupId);
            } catch (Exception e) {
                log.warn("Failed to delete consumer group from env {}: {}", env.getName(), e.getMessage());
            }
        }
    }

    /**
     * Get deployed APISIX service IDs for the given control-plane service IDs.
     */
    private List<String> getDeployedApisixServiceIds(String orgId, String envId, List<String> serviceIds) {
        return serviceIds.stream()
                .map(serviceId -> {
                    Service svc = apiServiceRepository.findById(serviceId).orElse(null);
                    if (svc == null) return null;

                    boolean deployed = deploymentRepository.existsByServiceIdAndEnvironmentId(serviceId, envId);
                    if (!deployed) {
                        log.warn("Service '{}' not deployed in env {}, skipping", svc.getName(), envId);
                        return null;
                    }
                    return svc.getId();
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private void createOrUpdateConsumerGroupInApisix(Environment environment, String groupId,
                                                     String displayName, List<String> serviceIds, String pluginConfigJson) {
        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();

        Map<String, Object> payload = new HashMap<>();
        payload.put("desc", "Product: " + displayName);

        Map<String, Object> plugins = new HashMap<>();

        if (!serviceIds.isEmpty()) {
            Map<String, Object> restriction = new HashMap<>();
            restriction.put("whitelist", serviceIds);
            restriction.put("type", "service_id");
            plugins.put("consumer-restriction", restriction);
        }

        if (pluginConfigJson != null && !pluginConfigJson.trim().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> customPlugins = mapper.readValue(pluginConfigJson, Map.class);
                plugins.putAll(customPlugins);
            } catch (Exception e) {
                throw new BusinessException("Invalid plugin configuration JSON: " + e.getMessage());
            }
        }

        payload.put("plugins", plugins);

        webClient.put()
                .uri("/apisix/admin/consumer_groups/{id}", groupId)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException("APISIX: " + resp.statusCode() + ": " + body))))
                .bodyToMono(String.class)
                .block();
    }

    private void deleteConsumerGroupFromApisix(Environment environment, String groupId) {
        WebClient webClient = webClientBuilder
                .baseUrl(environment.getApisixAdminUrl())
                .defaultHeader("X-API-KEY", adminKey)
                .build();

        try {
            webClient.delete()
                    .uri("/apisix/admin/consumer_groups/{id}", groupId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, resp -> Mono.empty())
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException("APISIX: " + body))))
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.warn("Failed to delete consumer group: {}", e.getMessage());
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
            log.warn("Failed to delete consumer: {}", e.getMessage());
        }
    }

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
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash string", e);
        }
    }

    private void validatePluginConfig(String pluginConfigJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readValue(pluginConfigJson, Map.class);
        } catch (Exception e) {
            throw new BusinessException("Invalid plugin configuration JSON: " + e.getMessage());
        }
    }
}
