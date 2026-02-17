package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateEnvironmentRequest;
import com.apisix.controlplane.entity.Environment;
import com.apisix.controlplane.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orgs/{orgId}/envs")
@RequiredArgsConstructor
@Hidden
@CrossOrigin(origins = "*")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    @PostMapping
    public ResponseEntity<Environment> createEnvironment(
            @PathVariable String orgId,
            @Valid @RequestBody CreateEnvironmentRequest request) {
        Environment environment = environmentService.createEnvironment(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(environment);
    }

    @GetMapping
    public ResponseEntity<List<Environment>> getEnvironmentsByOrg(@PathVariable String orgId) {
        return ResponseEntity.ok(environmentService.getEnvironmentsByOrg(orgId));
    }

    @GetMapping("/{envId}")
    public ResponseEntity<Environment> getEnvironmentById(@PathVariable String orgId, @PathVariable String envId) {
        return ResponseEntity.ok(environmentService.getEnvironmentById(envId));
    }

    @DeleteMapping("/{envId}")
    public ResponseEntity<Void> deleteEnvironment(@PathVariable String orgId, @PathVariable String envId) {
        environmentService.deleteEnvironment(envId);
        return ResponseEntity.noContent().build();
    }
}
