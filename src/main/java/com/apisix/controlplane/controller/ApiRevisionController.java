package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateApiRequest;
import com.apisix.controlplane.dto.DeploymentRequest;
import com.apisix.controlplane.entity.ApiRevision;
import com.apisix.controlplane.service.ApiRevisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/apis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiRevisionController {

    private final ApiRevisionService apiRevisionService;

    /**
     * Create a new API (creates revision 1)
     */
    @PostMapping
    public ResponseEntity<ApiRevision> createApi(
            @PathVariable String orgId,
            @Valid @RequestBody CreateApiRequest request) {
        ApiRevision revision = apiRevisionService.createApi(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(revision);
    }

    /**
     * Create a new revision of an existing API
     */
    @PostMapping("/{apiName}/revisions")
    public ResponseEntity<ApiRevision> createRevision(
            @PathVariable String orgId,
            @PathVariable String apiName,
            @Valid @RequestBody CreateApiRequest request) {
        ApiRevision revision = apiRevisionService.createRevision(orgId, apiName, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(revision);
    }

    /**
     * Get all APIs in an organization (grouped by name)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllApis(@PathVariable String orgId) {
        List<ApiRevision> allRevisions = apiRevisionService.getAllApisInOrg(orgId);
        
        // Group by API name
        Map<String, List<ApiRevision>> groupedApis = allRevisions.stream()
                .collect(Collectors.groupingBy(ApiRevision::getName));
        
        Map<String, Object> response = new HashMap<>();
        response.put("totalApis", groupedApis.size());
        response.put("totalRevisions", allRevisions.size());
        response.put("apis", groupedApis);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get all revisions of a specific API
     */
    @GetMapping("/{apiName}/revisions")
    public ResponseEntity<List<ApiRevision>> getApiRevisions(
            @PathVariable String orgId,
            @PathVariable String apiName) {
        List<ApiRevision> revisions = apiRevisionService.getApiRevisions(orgId, apiName);
        return ResponseEntity.ok(revisions);
    }

    /**
     * Get a specific revision by ID
     */
    @GetMapping("/revisions/{revisionId}")
    public ResponseEntity<ApiRevision> getRevisionById(@PathVariable String orgId, @PathVariable String revisionId) {
        ApiRevision revision = apiRevisionService.getRevisionById(revisionId);
        return ResponseEntity.ok(revision);
    }

    /**
     * Update a DRAFT revision
     */
    @PutMapping("/revisions/{revisionId}")
    public ResponseEntity<ApiRevision> updateRevision(
            @PathVariable String orgId,
            @PathVariable String revisionId,
            @Valid @RequestBody CreateApiRequest request) {
        ApiRevision revision = apiRevisionService.updateRevision(revisionId, request);
        return ResponseEntity.ok(revision);
    }

    /**
     * Delete a DRAFT revision
     */
    @DeleteMapping("/revisions/{revisionId}")
    public ResponseEntity<Void> deleteRevision(@PathVariable String orgId, @PathVariable String revisionId) {
        apiRevisionService.deleteRevision(revisionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clone a revision (creates a new DRAFT revision with the same configuration)
     */
    @PostMapping("/revisions/{revisionId}/clone")
    public ResponseEntity<ApiRevision> cloneRevision(
            @PathVariable String orgId,
            @PathVariable String revisionId) {
        ApiRevision clonedRevision = apiRevisionService.cloneRevision(revisionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(clonedRevision);
    }

    /**
     * Deploy a revision to selected environments
     */
    @PostMapping("/revisions/{revisionId}/deploy")
    public ResponseEntity<ApiRevision> deployRevision(
            @PathVariable String orgId,
            @PathVariable String revisionId,
            @Valid @RequestBody DeploymentRequest request) {
        ApiRevision revision = apiRevisionService.deployRevision(revisionId, request);
        return ResponseEntity.ok(revision);
    }

    /**
     * Undeploy a revision from selected environments
     */
    @PostMapping("/revisions/{revisionId}/undeploy")
    public ResponseEntity<ApiRevision> undeployRevision(
            @PathVariable String orgId,
            @PathVariable String revisionId,
            @Valid @RequestBody DeploymentRequest request) {
        ApiRevision revision = apiRevisionService.undeployRevision(revisionId, request);
        return ResponseEntity.ok(revision);
    }
}

