package com.apisix.controlplane.service;

import com.apisix.controlplane.dto.CreateProductRequest;
import com.apisix.controlplane.dto.ProductResponse;
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

    @Transactional
    public Product createProduct(String orgId, String envId, CreateProductRequest request) {
        log.info("Creating product '{}' for organization: {} in environment: {}", request.getName(), orgId, envId);

        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization not found: " + orgId);
        }

        Environment environment = environmentRepository.findById(envId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + envId));
        if (!environment.getOrgId().equals(orgId)) {
            throw new BusinessException("Environment does not belong to this organization");
        }

        if (productRepository.existsByOrgIdAndEnvIdAndName(orgId, envId, request.getName())) {
            throw new BusinessException("Product with name '" + request.getName() + "' already exists in this environment");
        }

        validateServiceIds(orgId, request.getServiceIds());

        Product product = Product.builder()
                .orgId(orgId)
                .envId(envId)
                .name(request.getName())
                .description(request.getDescription())
                .displayName(request.getDisplayName())
                .serviceIds(request.getServiceIds())
                .plugins(request.getPlugins())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Product saved = productRepository.save(product);
        log.info("Product created with ID: {}", saved.getId());

        createOrUpdateConsumerGroupForProduct(environment, saved);

        return saved;
    }

    @Transactional
    public Product updateProduct(String orgId, String envId, String productId, CreateProductRequest request) {
        Product product = getProductById(orgId, productId);

        if (!product.getEnvId().equals(envId)) {
            throw new BusinessException("Product does not belong to this environment");
        }

        validateServiceIds(orgId, request.getServiceIds());

        product.setDescription(request.getDescription());
        product.setDisplayName(request.getDisplayName());
        product.setServiceIds(request.getServiceIds());
        product.setPlugins(request.getPlugins());
        product.setUpdatedAt(LocalDateTime.now());

        Environment environment = environmentRepository.findById(envId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + envId));

        createOrUpdateConsumerGroupForProduct(environment, product);

        return productRepository.save(product);
    }

    public List<Product> getProductsByEnvironment(String orgId, String envId) {
        return productRepository.findByOrgIdAndEnvId(orgId, envId);
    }

    public Product getProductById(String orgId, String productId) {
        return productRepository.findByOrgIdAndId(orgId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    public ProductResponse toResponse(Product product) {
        List<Service> services = product.getServiceIds() == null
                ? List.of()
                : apiServiceRepository.findAllById(product.getServiceIds());
        return ProductResponse.fromEntity(product, services);
    }

    public List<ProductResponse> toResponseList(List<Product> products) {
        return products.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteProduct(String orgId, String envId, String productId, boolean force) {
        Product product = getProductById(orgId, productId);

        if (!product.getEnvId().equals(envId)) {
            throw new BusinessException("Product does not belong to this environment");
        }

        List<ProductSubscription> subscriptions = productSubscriptionRepository.findByOrgIdAndProductId(orgId, productId);

        if (!subscriptions.isEmpty() && !force) {
            throw new BusinessException("Cannot delete product. " + subscriptions.size() + " subscriptions exist. Use force delete.");
        }

        Environment environment = environmentRepository.findById(envId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + envId));

        if (force && !subscriptions.isEmpty()) {
            for (ProductSubscription sub : subscriptions) {
                try {
                    deleteConsumerFromApisix(environment, sub.getId());
                    productSubscriptionRepository.delete(sub);
                } catch (Exception e) {
                    log.error("Failed to delete subscription {}: {}", sub.getId(), e.getMessage());
                }
            }
        }

        deleteConsumerGroupFromApisix(environment, product.getId());
        productRepository.delete(product);
        log.info("Product deleted: {}", productId);
    }

    @Transactional
    public Product cloneProduct(String orgId, String sourceEnvId, String productId, String targetEnvId) {
        Product source = getProductById(orgId, productId);

        if (!source.getEnvId().equals(sourceEnvId)) {
            throw new BusinessException("Product does not belong to this environment");
        }

        Environment targetEnv = environmentRepository.findById(targetEnvId)
                .orElseThrow(() -> new ResourceNotFoundException("Target environment not found: " + targetEnvId));
        if (!targetEnv.getOrgId().equals(orgId)) {
            throw new BusinessException("Target environment does not belong to this organization");
        }

        if (sourceEnvId.equals(targetEnvId)) {
            throw new BusinessException("Cannot clone product to the same environment");
        }

        if (productRepository.existsByOrgIdAndEnvIdAndName(orgId, targetEnvId, source.getName())) {
            throw new BusinessException("Product with name '" + source.getName() + "' already exists in the target environment");
        }

        Product cloned = Product.builder()
                .orgId(orgId)
                .envId(targetEnvId)
                .name(source.getName())
                .description(source.getDescription())
                .displayName(source.getDisplayName())
                .serviceIds(new ArrayList<>(source.getServiceIds()))
                .plugins(source.getPlugins() != null ? new HashMap<>(source.getPlugins()) : null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Product saved = productRepository.save(cloned);
        log.info("Product cloned from {} to env {} with new ID: {}", productId, targetEnvId, saved.getId());

        createOrUpdateConsumerGroupForProduct(targetEnv, saved);

        return saved;
    }

    private void createOrUpdateConsumerGroupForProduct(Environment environment, Product product) {
        List<String> apisixServiceIds = getDeployedApisixServiceIds(product.getEnvId(), product.getServiceIds());
        createOrUpdateConsumerGroupInApisix(environment, product.getId(), product.getDisplayName(),
                apisixServiceIds, product.getPlugins());
    }

    private List<String> getDeployedApisixServiceIds(String envId, List<String> serviceIds) {
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
                                                     String displayName, List<String> serviceIds,
                                                     Map<String, Object> customPlugins) {
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

        if (customPlugins != null) {
            plugins.putAll(customPlugins);
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

    private void validateServiceIds(String orgId, List<String> serviceIds) {
        for (String serviceId : serviceIds) {
            Service svc = apiServiceRepository.findById(serviceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
            if (!svc.getOrgId().equals(orgId)) {
                throw new BusinessException("Service " + serviceId + " does not belong to this organization");
            }
        }
    }

}
