package com.apisix.controlplane.dto;

import com.apisix.controlplane.entity.Deployment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentResponse {

    private String environmentId;
    private String environmentName;
    private LocalDateTime deployedAt;

    public static DeploymentResponse from(Deployment deployment, String environmentName) {
        return DeploymentResponse.builder()
                .environmentId(deployment.getEnvironmentId())
                .environmentName(environmentName)
                .deployedAt(deployment.getDeployedAt())
                .build();
    }
}
