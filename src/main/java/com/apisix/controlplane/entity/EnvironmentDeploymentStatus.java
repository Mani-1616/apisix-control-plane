package com.apisix.controlplane.entity;

import com.apisix.controlplane.enums.RevisionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tracks deployment status and upstream configuration for a specific environment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentDeploymentStatus {
    
    /**
     * Deployment status for this environment
     * Uses the same enum as parent revision state
     */
    private RevisionState status;
    
    /**
     * Upstream ID used for this environment
     * Must be set before deployment
     */
    private String upstreamId;
    
    /**
     * When was this last deployed
     */
    private LocalDateTime lastDeployedAt;
    
    /**
     * When was this last undeployed (if applicable)
     */
    private LocalDateTime lastUndeployedAt;
    
    /**
     * User who performed the last deployment/undeployment
     */
    private String lastModifiedBy;
    
    /**
     * Additional notes or metadata
     */
    private String notes;
}

