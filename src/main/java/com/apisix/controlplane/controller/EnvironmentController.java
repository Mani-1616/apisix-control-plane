package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateApiRequest;
import com.apisix.controlplane.dto.CreateEnvironmentRequest;
import com.apisix.controlplane.entity.Environment;
import com.apisix.controlplane.entity.Upstream;
import com.apisix.controlplane.service.EnvironmentService;
import com.apisix.controlplane.service.UpstreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/environments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EnvironmentController {

    private final EnvironmentService environmentService;
    private final UpstreamService upstreamService;

    @PostMapping
    public ResponseEntity<Environment> createEnvironment(
            @PathVariable String orgId,
            @Valid @RequestBody CreateEnvironmentRequest request) {
        Environment environment = environmentService.createEnvironment(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(environment);
    }

    @GetMapping
    public ResponseEntity<List<Environment>> getEnvironmentsByOrg(@PathVariable String orgId) {
        List<Environment> environments = environmentService.getEnvironmentsByOrg(orgId);
        return ResponseEntity.ok(environments);
    }

    @GetMapping("/{envId}")
    public ResponseEntity<Environment> getEnvironmentById(@PathVariable String orgId, @PathVariable String envId) {
        Environment environment = environmentService.getEnvironmentById(envId);
        return ResponseEntity.ok(environment);
    }

    @DeleteMapping("/{envId}")
    public ResponseEntity<Void> deleteEnvironment(@PathVariable String orgId, @PathVariable String envId) {
        environmentService.deleteEnvironment(envId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Get all upstreams for an environment
     */
    @GetMapping("/{envId}/upstreams")
    public ResponseEntity<List<Upstream>> getUpstreamsByEnvironment(
            @PathVariable String orgId, 
            @PathVariable String envId) {
        List<Upstream> upstreams = upstreamService.getUpstreamsByEnvironment(envId);
        return ResponseEntity.ok(upstreams);
    }
    
    /**
     * Create a new upstream in an environment
     * Upstream is immediately created in APISIX
     */
    @PostMapping("/{envId}/upstreams")
    public ResponseEntity<Upstream> createUpstream(
            @PathVariable String orgId,
            @PathVariable String envId,
            @Valid @RequestBody CreateApiRequest.UpstreamConfigDto upstreamConfig) {
        Upstream upstream = upstreamService.createUpstream(envId, upstreamConfig);
        return ResponseEntity.status(HttpStatus.CREATED).body(upstream);
    }
    
    /**
     * Delete an unused upstream from an environment
     */
    @DeleteMapping("/{envId}/upstreams/{upstreamId}")
    public ResponseEntity<Void> deleteUpstream(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String upstreamId) {
        upstreamService.deleteUpstream(upstreamId);
        return ResponseEntity.noContent().build();
    }
}

