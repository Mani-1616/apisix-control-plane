package com.apisix.controlplane.dto;

import com.apisix.controlplane.entity.Service;
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
public class ServiceWithRevisionsResponse {

    private String id;
    private String name;
    private String displayName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<RevisionSummary> revisions;

    public static ServiceWithRevisionsResponse fromEntity(Service service, List<RevisionSummary> revisions) {
        return ServiceWithRevisionsResponse.builder()
                .id(service.getId())
                .name(service.getName())
                .displayName(service.getDisplayName())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .revisions(revisions)
                .build();
    }
}
