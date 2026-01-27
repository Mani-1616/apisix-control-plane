package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateDeveloperRequest;
import com.apisix.controlplane.entity.Developer;
import com.apisix.controlplane.service.DeveloperService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/developers")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DeveloperController {
    
    private final DeveloperService developerService;
    
    @PostMapping
    public ResponseEntity<Developer> createDeveloper(
            @PathVariable String orgId,
            @Valid @RequestBody CreateDeveloperRequest request) {
        log.info("POST /api/v1/organizations/{}/developers - Creating developer", orgId);
        Developer developer = developerService.createDeveloper(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(developer);
    }
    
    @GetMapping
    public ResponseEntity<List<Developer>> getDevelopers(@PathVariable String orgId) {
        log.info("GET /api/v1/organizations/{}/developers - Fetching all developers", orgId);
        List<Developer> developers = developerService.getDevelopersByOrganization(orgId);
        return ResponseEntity.ok(developers);
    }
    
    @GetMapping("/{developerId}")
    public ResponseEntity<Developer> getDeveloper(
            @PathVariable String orgId,
            @PathVariable String developerId) {
        log.info("GET /api/v1/organizations/{}/developers/{} - Fetching developer", orgId, developerId);
        Developer developer = developerService.getDeveloperById(orgId, developerId);
        return ResponseEntity.ok(developer);
    }
    
    @PutMapping("/{developerId}")
    public ResponseEntity<Developer> updateDeveloper(
            @PathVariable String orgId,
            @PathVariable String developerId,
            @Valid @RequestBody CreateDeveloperRequest request) {
        log.info("PUT /api/v1/organizations/{}/developers/{} - Updating developer", orgId, developerId);
        Developer developer = developerService.updateDeveloper(orgId, developerId, request);
        return ResponseEntity.ok(developer);
    }
    
    @DeleteMapping("/{developerId}")
    public ResponseEntity<Void> deleteDeveloper(
            @PathVariable String orgId,
            @PathVariable String developerId) {
        log.info("DELETE /api/v1/organizations/{}/developers/{} - Deleting developer", orgId, developerId);
        developerService.deleteDeveloper(orgId, developerId);
        return ResponseEntity.noContent().build();
    }
}

