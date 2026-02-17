package com.apisix.controlplane.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DeploymentRequest {

    /**
     * Environment-to-upstream mappings.
     * Must be provided for environments being deployed to.
     */
    private List<@Valid EnvironmentUpstreamMapping> environmentUpstreams;
    
    /**
     * List of environment IDs to deploy to or undeploy from
     */
    @NotEmpty(message = "At least one environment is required")
    private List<String> environmentIds;
    
    /**
     * Force deployment even if already deployed
     */
    private boolean force;
}
