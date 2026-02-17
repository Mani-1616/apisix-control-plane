package com.apisix.controlplane.dto;

import com.apisix.controlplane.entity.ServiceRevision;
import com.apisix.controlplane.enums.RevisionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lightweight revision DTO for the services overview endpoint.
 * Same as {@link ServiceRevisionResponse} but without serviceSpecification and routeSpecifications.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisionSummary {

    private String id;
    private Integer revisionNumber;
    private RevisionState state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DeploymentResponse> deployments;
    private List<UpstreamBindingResponse> upstreamBindings;

    public static RevisionSummary fromEntity(ServiceRevision revision,
                                             List<DeploymentResponse> deployments,
                                             List<UpstreamBindingResponse> upstreamBindings) {
        return RevisionSummary.builder()
                .id(revision.getId())
                .revisionNumber(revision.getRevisionNumber())
                .state(revision.getState())
                .createdAt(revision.getCreatedAt())
                .updatedAt(revision.getUpdatedAt())
                .deployments(deployments)
                .upstreamBindings(upstreamBindings)
                .build();
    }
}
