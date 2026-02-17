package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateUpstreamRequest;
import com.apisix.controlplane.dto.PaginatedResponse;
import com.apisix.controlplane.dto.PaginationRequest;
import com.apisix.controlplane.entity.Upstream;
import com.apisix.controlplane.service.UpstreamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orgs/{orgId}/envs/{envId}/upstreams")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Upstreams")
public class UpstreamController {

    private final UpstreamService upstreamService;

    @GetMapping
    public ResponseEntity<PaginatedResponse<Upstream>> getUpstreamsByEnvironment(
            @PathVariable String orgId,
            @PathVariable String envId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        Page<Upstream> page = upstreamService.getUpstreamsByEnvironment(
                envId, pagination.toPageable().withSort(Sort.by(Sort.Direction.ASC, "name")));
        return ResponseEntity.ok(PaginatedResponse.from(page, page.getContent()));
    }

    @GetMapping("/{upstreamId}")
    public ResponseEntity<Upstream> getUpstreamById(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String upstreamId) {
        return ResponseEntity.ok(upstreamService.getUpstreamById(upstreamId));
    }

    @PostMapping
    public ResponseEntity<Upstream> createUpstream(
            @PathVariable String orgId,
            @PathVariable String envId,
            @Valid @RequestBody CreateUpstreamRequest upstreamConfig) {
        Upstream upstream = upstreamService.createUpstream(envId, upstreamConfig);
        return ResponseEntity.status(HttpStatus.CREATED).body(upstream);
    }

    @DeleteMapping("/{upstreamId}")
    public ResponseEntity<Void> deleteUpstream(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String upstreamId) {
        upstreamService.deleteUpstream(upstreamId);
        return ResponseEntity.noContent().build();
    }
}
