package com.apisix.controlplane.dto;

import com.apisix.controlplane.entity.Api;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiWithRevisionsResponse {

    private String id;
    private String name;
    private String displayName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<RevisionSummary> revisions;

    public static ApiWithRevisionsResponse fromEntity(Api api, List<RevisionSummary> revisions) {
        return ApiWithRevisionsResponse.builder()
                .id(api.getId())
                .name(api.getName())
                .displayName(api.getDisplayName())
                .createdAt(api.getCreatedAt())
                .updatedAt(api.getUpdatedAt())
                .revisions(revisions)
                .build();
    }
}
