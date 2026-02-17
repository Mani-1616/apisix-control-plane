package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateProductSubscriptionRequest;
import com.apisix.controlplane.entity.ProductSubscription;
import com.apisix.controlplane.service.ProductSubscriptionService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orgs/{orgId}/product-subscriptions")
@RequiredArgsConstructor
@Slf4j
@Hidden
@CrossOrigin(origins = "*")
public class ProductSubscriptionController {
    
    private final ProductSubscriptionService subscriptionService;
    
    @PostMapping
    public ResponseEntity<ProductSubscription> createSubscription(
            @PathVariable String orgId,
            @Valid @RequestBody CreateProductSubscriptionRequest request) {
        log.info("POST /api/v1/organizations/{}/product-subscriptions - Creating subscription", orgId);
        ProductSubscription subscription = subscriptionService.createSubscription(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscription);
    }
    
    @GetMapping
    public ResponseEntity<List<ProductSubscription>> getAllSubscriptions(
            @PathVariable String orgId,
            @RequestParam(required = false) String developerId,
            @RequestParam(required = false) String envId) {
        log.info("GET /api/v1/organizations/{}/product-subscriptions - Fetching subscriptions (developerId: {}, envId: {})",
                orgId, developerId, envId);
        List<ProductSubscription> subscriptions = subscriptionService.getSubscriptionsByOrganization(orgId, developerId, envId);
        return ResponseEntity.ok(subscriptions);
    }
    
    @PutMapping("/{subscriptionId}/grant")
    public ResponseEntity<ProductSubscription> grantSubscription(
            @PathVariable String orgId,
            @PathVariable String subscriptionId) {
        log.info("PUT /api/v1/organizations/{}/product-subscriptions/{}/grant - Granting subscription", orgId, subscriptionId);
        ProductSubscription subscription = subscriptionService.grantSubscription(orgId, subscriptionId);
        return ResponseEntity.ok(subscription);
    }
    
    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> revokeSubscription(
            @PathVariable String orgId,
            @PathVariable String subscriptionId) {
        log.info("DELETE /api/v1/organizations/{}/product-subscriptions/{} - Revoking subscription", orgId, subscriptionId);
        subscriptionService.revokeSubscription(orgId, subscriptionId);
        return ResponseEntity.noContent().build();
    }
}

