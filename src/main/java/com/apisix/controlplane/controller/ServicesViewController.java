package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.ApiWithRevisionsResponse;
import com.apisix.controlplane.dto.PaginatedResponse;
import com.apisix.controlplane.dto.PaginationRequest;
import com.apisix.controlplane.dto.RevisionSummary;
import com.apisix.controlplane.entity.Api;
import com.apisix.controlplane.service.ApiService;
import com.apisix.controlplane.service.ServiceRevisionService;
import io.swagger.v3.oas.annotations.Hidden;
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
@RequestMapping("/api/orgs/{orgId}/apis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Services Overview")
@Hidden
public class ServicesViewController {

    private final ApiService apiService;
    private final ServiceRevisionService revisionService;

    @GetMapping("/overview")
    public ResponseEntity<PaginatedResponse<ApiWithRevisionsResponse>> getServicesOverview(
            @PathVariable String orgId,
            @Valid @ModelAttribute PaginationRequest pagination) {

        Page<Api> apiPage = apiService.getApisByOrg(
                orgId, pagination.toPageable().withSort(Sort.by(Sort.Direction.ASC, "name")));

        List<String> apiIds = apiPage.getContent().stream().map(Api::getId).toList();
        Map<String, List<RevisionSummary>> revisionsByApi = revisionService.getRevisionSummariesByApiIds(apiIds);

        List<ApiWithRevisionsResponse> content = apiPage.getContent().stream()
                .map(api -> ApiWithRevisionsResponse.fromEntity(
                        api,
                        revisionsByApi.getOrDefault(api.getId(), List.of())
                ))
                .toList();

        return ResponseEntity.ok(PaginatedResponse.from(apiPage, content));
    }
}
