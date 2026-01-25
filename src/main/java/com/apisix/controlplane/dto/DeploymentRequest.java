package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DeploymentRequest {
    
    /**
     * Map of environment ID to upstream ID
     * Must be provided for environments being deployed to
     * Key: environmentId, Value: upstreamId
     */
    private Map<String, String> environmentUpstreams;
    
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
