package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateProductSubscriptionRequest;
import com.apisix.controlplane.dto.PaginatedResponse;
import com.apisix.controlplane.dto.PaginationRequest;
import com.apisix.controlplane.entity.ProductSubscription;
import com.apisix.controlplane.service.ProductSubscriptionService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orgs/{orgId}")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product Subscriptions")
@CrossOrigin(origins = "*")
public class ProductSubscriptionController {

    private final ProductSubscriptionService subscriptionService;

    @PostMapping("/envs/{envId}/products/{productId}/subscriptions")
    public ResponseEntity<ProductSubscription> createSubscription(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String productId,
            @Valid @RequestBody CreateProductSubscriptionRequest request) {
        log.info("POST /api/orgs/{}/envs/{}/products/{}/subscriptions - Creating subscription", orgId, envId, productId);
        ProductSubscription subscription = subscriptionService.createSubscription(orgId, envId, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscription);
    }

    @GetMapping("/products/subscriptions")
    public ResponseEntity<PaginatedResponse<ProductSubscription>> getSubscriptionsByOrg(
            @PathVariable String orgId,
            @RequestParam(required = false) String developerId,
            @RequestParam(required = false) String envId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        log.info("GET /api/orgs/{}/products/subscriptions - Fetching subscriptions (developerId: {}, envId: {})",
                orgId, developerId, envId);
        Page<ProductSubscription> page = subscriptionService.getSubscriptionsByOrganization(
                orgId, envId, developerId,
                pagination.toPageable().withSort(Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(PaginatedResponse.from(page, page.getContent()));
    }

    @GetMapping("/envs/{envId}/products/subscriptions")
    public ResponseEntity<PaginatedResponse<ProductSubscription>> getSubscriptionsByEnv(
            @PathVariable String orgId,
            @PathVariable String envId,
            @RequestParam(required = false) String developerId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        log.info("GET /api/orgs/{}/envs/{}/products/subscriptions - Fetching subscriptions (developerId: {})",
                orgId, envId, developerId);
        Page<ProductSubscription> page = subscriptionService.getSubscriptionsByOrganization(
                orgId, envId, developerId,
                pagination.toPageable().withSort(Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(PaginatedResponse.from(page, page.getContent()));
    }

    @GetMapping("/envs/{envId}/products/{productId}/subscriptions")
    public ResponseEntity<PaginatedResponse<ProductSubscription>> getSubscriptionsByProduct(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String productId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        log.info("GET /api/orgs/{}/envs/{}/products/{}/subscriptions - Fetching subscriptions for product",
                orgId, envId, productId);
        Page<ProductSubscription> page = subscriptionService.getSubscriptionsByProduct(
                orgId, productId,
                pagination.toPageable().withSort(Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(PaginatedResponse.from(page, page.getContent()));
    }

    @Hidden
    @PutMapping("/envs/{envId}/products/{productId}/subscriptions/{subscriptionId}/grant")
    public ResponseEntity<ProductSubscription> grantSubscription(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String productId,
            @PathVariable String subscriptionId) {
        log.info("PUT /api/orgs/{}/envs/{}/products/{}/subscriptions/{}/grant - Granting subscription",
                orgId, envId, productId, subscriptionId);
        ProductSubscription subscription = subscriptionService.grantSubscription(orgId, subscriptionId);
        return ResponseEntity.ok(subscription);
    }

    @DeleteMapping("/envs/{envId}/products/{productId}/subscriptions/{subscriptionId}")
    public ResponseEntity<Void> revokeSubscription(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String productId,
            @PathVariable String subscriptionId) {
        log.info("DELETE /api/orgs/{}/envs/{}/products/{}/subscriptions/{} - Revoking subscription",
                orgId, envId, productId, subscriptionId);
        subscriptionService.revokeSubscription(orgId, subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
