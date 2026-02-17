package com.apisix.controlplane.dto;

import com.apisix.controlplane.apisix.model.RouteSpec;
import com.apisix.controlplane.apisix.model.ServiceSpec;
import com.apisix.controlplane.entity.ServiceRevision;
import com.apisix.controlplane.enums.RevisionState;
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
public class ServiceRevisionResponse {

    private String id;
    private String orgId;
    private String serviceId;
    private Integer revisionNumber;
    private RevisionState state;
    private ServiceSpec serviceSpecification;
    private List<RouteSpec> routeSpecifications;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DeploymentResponse> deployments;
    private List<UpstreamBindingResponse> upstreamBindings;

    public static ServiceRevisionResponse fromEntity(ServiceRevision revision,
                                                     List<DeploymentResponse> deployments,
                                                     List<UpstreamBindingResponse> upstreamBindings) {
        return ServiceRevisionResponse.builder()
                .id(revision.getId())
                .orgId(revision.getOrgId())
                .serviceId(revision.getServiceId())
                .revisionNumber(revision.getRevisionNumber())
                .state(revision.getState())
                .serviceSpecification(revision.getServiceSpecification())
                .routeSpecifications(revision.getRouteSpecifications())
                .createdAt(revision.getCreatedAt())
                .updatedAt(revision.getUpdatedAt())
                .deployments(deployments)
                .upstreamBindings(upstreamBindings)
                .build();
    }
}
