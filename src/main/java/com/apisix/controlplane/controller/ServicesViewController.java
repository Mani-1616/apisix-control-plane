package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.PaginatedResponse;
import com.apisix.controlplane.dto.PaginationRequest;
import com.apisix.controlplane.dto.RevisionSummary;
import com.apisix.controlplane.dto.ServiceWithRevisionsResponse;
import com.apisix.controlplane.entity.Service;
import com.apisix.controlplane.service.ApiServiceService;
import com.apisix.controlplane.service.ServiceRevisionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orgs/{orgId}/services")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Services Overview")
public class ServicesViewController {

    private final ApiServiceService apiServiceService;
    private final ServiceRevisionService revisionService;

    @GetMapping("/overview")
    public ResponseEntity<PaginatedResponse<ServiceWithRevisionsResponse>> getServicesOverview(
            @PathVariable String orgId,
            @Valid @ModelAttribute PaginationRequest pagination) {

        // 1. Get paginated services
        Page<Service> servicePage = apiServiceService.getServicesByOrg(
                orgId, pagination.toPageable().withSort(Sort.by(Sort.Direction.ASC, "name")));

        // 2. Batch-fetch revision summaries from ServiceRevisionService
        List<String> serviceIds = servicePage.getContent().stream().map(Service::getId).toList();
        Map<String, List<RevisionSummary>> revisionsByService = revisionService.getRevisionSummariesByServiceIds(serviceIds);

        // 3. Assemble composite response
        List<ServiceWithRevisionsResponse> content = servicePage.getContent().stream()
                .map(svc -> ServiceWithRevisionsResponse.fromEntity(
                        svc,
                        revisionsByService.getOrDefault(svc.getId(), List.of())
                ))
                .toList();

        return ResponseEntity.ok(PaginatedResponse.from(servicePage, content));
    }
}
