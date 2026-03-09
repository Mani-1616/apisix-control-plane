package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateProductSubscriptionRequest;
import com.apisix.controlplane.entity.ProductSubscription;
import com.apisix.controlplane.service.ProductSubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orgs/{orgId}/envs/{envId}/products/{productId}/subscriptions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product Subscriptions")
@CrossOrigin(origins = "*")
public class ProductSubscriptionController {
    
    private final ProductSubscriptionService subscriptionService;
    
    @PostMapping
    public ResponseEntity<ProductSubscription> createSubscription(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String productId,
            @Valid @RequestBody CreateProductSubscriptionRequest request) {
        log.info("POST /api/orgs/{}/envs/{}/products/{}/subscriptions - Creating subscription", orgId, envId, productId);
        ProductSubscription subscription = subscriptionService.createSubscription(orgId, envId, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscription);
    }
    
    @GetMapping
    public ResponseEntity<List<ProductSubscription>> getAllSubscriptions(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String productId,
            @RequestParam(required = false) String developerId) {
        log.info("GET /api/orgs/{}/envs/{}/products/{}/subscriptions - Fetching subscriptions (developerId: {})",
                orgId, envId, productId, developerId);
        List<ProductSubscription> subscriptions = subscriptionService.getSubscriptionsByOrganization(orgId, envId, developerId);
        return ResponseEntity.ok(subscriptions);
    }
    
    @PutMapping("/{subscriptionId}/grant")
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
    
    @DeleteMapping("/{subscriptionId}")
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
