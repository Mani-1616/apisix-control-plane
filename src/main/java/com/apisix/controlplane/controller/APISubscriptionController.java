package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateSubscriptionRequest;
import com.apisix.controlplane.dto.PaginatedResponse;
import com.apisix.controlplane.dto.PaginationRequest;
import com.apisix.controlplane.entity.APISubscription;
import com.apisix.controlplane.service.APISubscriptionService;
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
@Tag(name = "API Subscriptions")
@CrossOrigin(origins = "*")
public class APISubscriptionController {

    private final APISubscriptionService subscriptionService;

    @PostMapping("/services/{serviceId}/subscriptions")
    public ResponseEntity<APISubscription> createSubscription(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        log.info("POST /api/orgs/{}/services/{}/subscriptions - Creating subscription", orgId, serviceId);
        APISubscription subscription = subscriptionService.createSubscription(orgId, serviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscription);
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<PaginatedResponse<APISubscription>> getAllSubscriptions(
            @PathVariable String orgId,
            @RequestParam(required = false) String developerId,
            @RequestParam(required = false) String envId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        log.info("GET /api/orgs/{}/subscriptions - Fetching subscriptions (developerId: {}, envId: {})",
                orgId, developerId, envId);
        Page<APISubscription> page = subscriptionService.getSubscriptionsByOrganization(
                orgId, developerId, envId,
                pagination.toPageable().withSort(Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(PaginatedResponse.from(page, page.getContent()));
    }

    @GetMapping("/services/{serviceId}/subscriptions")
    public ResponseEntity<PaginatedResponse<APISubscription>> getSubscriptionsByService(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        log.info("GET /api/orgs/{}/services/{}/subscriptions - Fetching subscriptions for service", orgId, serviceId);
        Page<APISubscription> page = subscriptionService.getSubscriptionsByService(
                orgId, serviceId,
                pagination.toPageable().withSort(Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(PaginatedResponse.from(page, page.getContent()));
    }

    @DeleteMapping("/services/{serviceId}/subscriptions/{subscriptionId}")
    public ResponseEntity<Void> revokeSubscription(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @PathVariable String subscriptionId) {
        log.info("DELETE /api/orgs/{}/services/{}/subscriptions/{} - Revoking subscription", orgId, serviceId, subscriptionId);
        subscriptionService.revokeSubscription(orgId, subscriptionId);
        return ResponseEntity.noContent().build();
    }

    @Hidden
    @PutMapping("/services/{serviceId}/subscriptions/{subscriptionId}/grant")
    public ResponseEntity<APISubscription> grantSubscription(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @PathVariable String subscriptionId) {
        log.info("PUT /api/orgs/{}/services/{}/subscriptions/{}/grant - Granting subscription", orgId, serviceId, subscriptionId);
        APISubscription subscription = subscriptionService.grantSubscription(orgId, subscriptionId);
        return ResponseEntity.ok(subscription);
    }
}
