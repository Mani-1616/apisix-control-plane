package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeploymentRequest {

    /**
     * Environment ID to deploy to or undeploy from
     */
    @NotBlank(message = "Environment ID is required")
    private String environmentId;

    /**
     * Force deployment even if already deployed
     */
    private boolean force;
}
