package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateServiceRequest;
import com.apisix.controlplane.dto.PaginatedResponse;
import com.apisix.controlplane.dto.PaginationRequest;
import com.apisix.controlplane.entity.Service;
import com.apisix.controlplane.service.ApiServiceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orgs/{orgId}/services")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Services")
public class ServiceController {

    private final ApiServiceService apiServiceService;

    @PostMapping
    public ResponseEntity<Service> createService(
            @PathVariable String orgId,
            @Valid @RequestBody CreateServiceRequest request) {
        Service service = apiServiceService.createService(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(service);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<Service>> getAllServices(
            @PathVariable String orgId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        Page<Service> page = apiServiceService.getServicesByOrg(
                orgId, pagination.toPageable().withSort(Sort.by(Sort.Direction.ASC, "name")));
        return ResponseEntity.ok(PaginatedResponse.from(page, page.getContent()));
    }

    @GetMapping("/{serviceId}")
    public ResponseEntity<Service> getService(
            @PathVariable String orgId,
            @PathVariable String serviceId) {
        return ResponseEntity.ok(apiServiceService.getServiceById(serviceId));
    }

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> deleteService(
            @PathVariable String orgId,
            @PathVariable String serviceId) {
        apiServiceService.deleteService(serviceId);
        return ResponseEntity.noContent().build();
    }
}
