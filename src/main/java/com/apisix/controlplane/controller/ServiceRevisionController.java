package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateServiceRevisionRequest;
import com.apisix.controlplane.dto.DeploymentRequest;
import com.apisix.controlplane.dto.PaginatedResponse;
import com.apisix.controlplane.dto.PaginationRequest;
import com.apisix.controlplane.dto.ServiceRevisionResponse;
import com.apisix.controlplane.service.ServiceRevisionService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orgs/{orgId}/services/{serviceId}/revisions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Service Revisions")
public class ServiceRevisionController {

    private final ServiceRevisionService revisionService;

    @PostMapping
    public ResponseEntity<ServiceRevisionResponse> createRevision(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @Valid @RequestBody CreateServiceRevisionRequest request) {
        ServiceRevisionResponse revision = revisionService.createRevision(serviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(revision);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<ServiceRevisionResponse>> getRevisions(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        return ResponseEntity.ok(revisionService.getRevisionsByService(
                serviceId, pagination.toPageable().withSort(Sort.by(Sort.Direction.DESC, "revisionNumber"))));
    }

    @GetMapping("/{revisionId}")
    public ResponseEntity<ServiceRevisionResponse> getRevision(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @PathVariable String revisionId) {
        return ResponseEntity.ok(revisionService.getRevisionById(revisionId));
    }

    @PutMapping("/{revisionId}")
    public ResponseEntity<ServiceRevisionResponse> updateRevision(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @PathVariable String revisionId,
            @Valid @RequestBody CreateServiceRevisionRequest request) {
        return ResponseEntity.ok(revisionService.updateRevision(revisionId, request));
    }

    @DeleteMapping("/{revisionId}")
    public ResponseEntity<Void> deleteRevision(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @PathVariable String revisionId) {
        revisionService.deleteRevision(revisionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{revisionId}/clone")
    public ResponseEntity<ServiceRevisionResponse> cloneRevision(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @PathVariable String revisionId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(revisionService.cloneRevision(revisionId));
    }

    @Hidden
    @PostMapping("/{revisionId}/deploy")
    public ResponseEntity<ServiceRevisionResponse> deployRevision(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @PathVariable String revisionId,
            @Valid @RequestBody DeploymentRequest request) {
        return ResponseEntity.ok(revisionService.deployRevision(revisionId, request));
    }

    @Hidden
    @PostMapping("/{revisionId}/undeploy")
    public ResponseEntity<ServiceRevisionResponse> undeployRevision(
            @PathVariable String orgId,
            @PathVariable String serviceId,
            @PathVariable String revisionId,
            @Valid @RequestBody DeploymentRequest request) {
        return ResponseEntity.ok(revisionService.undeployRevision(revisionId, request));
    }
}
