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

    @PostMapping("/apis/{apiId}/subscriptions")
    public ResponseEntity<APISubscription> createSubscription(
            @PathVariable String orgId,
            @PathVariable String apiId,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        log.info("POST /api/orgs/{}/apis/{}/subscriptions - Creating subscription", orgId, apiId);
        APISubscription subscription = subscriptionService.createSubscription(orgId, apiId, request);
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

    @GetMapping("/apis/{apiId}/subscriptions")
    public ResponseEntity<PaginatedResponse<APISubscription>> getSubscriptionsByApi(
            @PathVariable String orgId,
            @PathVariable String apiId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        log.info("GET /api/orgs/{}/apis/{}/subscriptions - Fetching subscriptions for API", orgId, apiId);
        Page<APISubscription> page = subscriptionService.getSubscriptionsByApi(
                orgId, apiId,
                pagination.toPageable().withSort(Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(PaginatedResponse.from(page, page.getContent()));
    }

    @DeleteMapping("/apis/{apiId}/subscriptions/{subscriptionId}")
    public ResponseEntity<Void> revokeSubscription(
            @PathVariable String orgId,
            @PathVariable String apiId,
            @PathVariable String subscriptionId) {
        log.info("DELETE /api/orgs/{}/apis/{}/subscriptions/{} - Revoking subscription", orgId, apiId, subscriptionId);
        subscriptionService.revokeSubscription(orgId, subscriptionId);
        return ResponseEntity.noContent().build();
    }

    @Hidden
    @PutMapping("/apis/{apiId}/subscriptions/{subscriptionId}/grant")
    public ResponseEntity<APISubscription> grantSubscription(
            @PathVariable String orgId,
            @PathVariable String apiId,
            @PathVariable String subscriptionId) {
        log.info("PUT /api/orgs/{}/apis/{}/subscriptions/{}/grant - Granting subscription", orgId, apiId, subscriptionId);
        APISubscription subscription = subscriptionService.grantSubscription(orgId, subscriptionId);
        return ResponseEntity.ok(subscription);
    }
}
