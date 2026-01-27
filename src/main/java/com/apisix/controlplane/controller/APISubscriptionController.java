package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateSubscriptionRequest;
import com.apisix.controlplane.entity.APISubscription;
import com.apisix.controlplane.service.APISubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/subscriptions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class APISubscriptionController {
    
    private final APISubscriptionService subscriptionService;
    
    @PostMapping
    public ResponseEntity<APISubscription> createSubscription(
            @PathVariable String orgId,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        log.info("POST /api/v1/organizations/{}/subscriptions - Creating subscription", orgId);
        APISubscription subscription = subscriptionService.createSubscription(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscription);
    }
    
    @GetMapping
    public ResponseEntity<List<APISubscription>> getAllSubscriptions(
            @PathVariable String orgId,
            @RequestParam(required = false) String developerId,
            @RequestParam(required = false) String envId) {
        log.info("GET /api/v1/organizations/{}/subscriptions - Fetching subscriptions (developerId: {}, envId: {})", 
                orgId, developerId, envId);
        List<APISubscription> subscriptions = subscriptionService.getSubscriptionsByOrganization(orgId, developerId, envId);
        return ResponseEntity.ok(subscriptions);
    }
    
    @GetMapping("/developer/{developerId}")
    public ResponseEntity<List<APISubscription>> getSubscriptionsByDeveloper(
            @PathVariable String orgId,
            @PathVariable String developerId,
            @RequestParam(required = false) String envId) {
        log.info("GET /api/v1/organizations/{}/subscriptions/developer/{} - Fetching subscriptions (envId: {})", 
                orgId, developerId, envId);
        List<APISubscription> subscriptions = subscriptionService.getSubscriptionsByDeveloper(orgId, developerId, envId);
        return ResponseEntity.ok(subscriptions);
    }
    
    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> revokeSubscription(
            @PathVariable String orgId,
            @PathVariable String subscriptionId) {
        log.info("DELETE /api/v1/organizations/{}/subscriptions/{} - Revoking subscription", orgId, subscriptionId);
        subscriptionService.revokeSubscription(orgId, subscriptionId);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/{subscriptionId}/grant")
    public ResponseEntity<APISubscription> grantSubscription(
            @PathVariable String orgId,
            @PathVariable String subscriptionId) {
        log.info("PUT /api/v1/organizations/{}/subscriptions/{}/grant - Granting subscription", orgId, subscriptionId);
        APISubscription subscription = subscriptionService.grantSubscription(orgId, subscriptionId);
        return ResponseEntity.ok(subscription);
    }
}

