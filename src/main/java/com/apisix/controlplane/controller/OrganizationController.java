package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateOrgRequest;
import com.apisix.controlplane.entity.Organization;
import com.apisix.controlplane.service.OrganizationService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orgs")
@RequiredArgsConstructor
@Hidden
@CrossOrigin(origins = "*")
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<Organization> createOrganization(@Valid @RequestBody CreateOrgRequest request) {
        Organization org = organizationService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(org);
    }

    @GetMapping
    public ResponseEntity<List<Organization>> getAllOrganizations() {
        List<Organization> orgs = organizationService.getAllOrganizations();
        return ResponseEntity.ok(orgs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Organization> getOrganizationById(@PathVariable String id) {
        Organization org = organizationService.getOrganizationById(id);
        return ResponseEntity.ok(org);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable String id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }
}

