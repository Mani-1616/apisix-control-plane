package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateApiRequest;
import com.apisix.controlplane.dto.PaginatedResponse;
import com.apisix.controlplane.dto.PaginationRequest;
import com.apisix.controlplane.entity.Api;
import com.apisix.controlplane.service.ApiService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orgs/{orgId}/apis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Hidden
public class ApiController {

    private final ApiService apiService;

    @PostMapping
    public ResponseEntity<Api> createApi(
            @PathVariable String orgId,
            @Valid @RequestBody CreateApiRequest request) {
        Api api = apiService.createApi(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(api);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<Api>> getAllApis(
            @PathVariable String orgId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        Page<Api> page = apiService.getApisByOrg(
                orgId, pagination.toPageable().withSort(Sort.by(Sort.Direction.ASC, "name")));
        return ResponseEntity.ok(PaginatedResponse.from(page, page.getContent()));
    }

    @GetMapping("/{apiId}")
    public ResponseEntity<Api> getApi(
            @PathVariable String orgId,
            @PathVariable String apiId) {
        return ResponseEntity.ok(apiService.getApiById(apiId));
    }

    @DeleteMapping("/{apiId}")
    public ResponseEntity<Void> deleteApi(
            @PathVariable String orgId,
            @PathVariable String apiId) {
        apiService.deleteApi(apiId);
        return ResponseEntity.noContent().build();
    }
}
