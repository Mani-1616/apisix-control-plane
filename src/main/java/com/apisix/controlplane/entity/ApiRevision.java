package com.apisix.controlplane.entity;

import com.apisix.controlplane.enums.RevisionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "api-revisions")
@CompoundIndex(name = "org_api_revision", def = "{'orgId': 1, 'name': 1, 'revisionNumber': 1}", unique = true)
public class ApiRevision {

    @Id
    private String id;

    private String orgId;
    private String name; // API name (unique within org)
    private Integer revisionNumber; // 1, 2, 3, etc.
    private String description;

    /**
     * Overall revision state - derived from environments map
     * DRAFT: All environments are DRAFT or empty
     * DEPLOYED: At least one environment is DEPLOYED
     * UNDEPLOYED: Was deployed but all environments are now UNDEPLOYED
     */
    @Builder.Default
    private RevisionState state = RevisionState.DRAFT;

    /**
     * Map of environment ID to deployment status
     * Key: environmentId
     * Value: EnvironmentDeploymentStatus (contains status, upstreamId, timestamps)
     */
    @Builder.Default
    private Map<String, EnvironmentDeploymentStatus> environments = new HashMap<>();

    // APISIX Service Configuration (plugins and metadata only)
    private ServiceConfig serviceConfig;

    // APISIX Routes Configuration
    private List<RouteConfig> routes;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceConfig {
        private Map<String, Object> plugins; // APISIX plugins
        private Map<String, Object> metadata; // Additional service metadata
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteConfig {
        private String name;
        private List<String> methods; // GET, POST, etc.
        private List<String> uris; // URI paths
        private Map<String, Object> plugins; // Route-specific plugins
        private Map<String, Object> metadata; // Additional route metadata
    }
    
    /**
     * Calculate and update the overall revision state based on environment statuses
     */
    public void calculateState() {
        if (environments == null || environments.isEmpty()) {
            this.state = RevisionState.DRAFT;
            return;
        }
        
        boolean hasDeployed = false;
        boolean hasUndeployed = false;
        
        for (EnvironmentDeploymentStatus envStatus : environments.values()) {
            if (envStatus.getStatus() == RevisionState.DEPLOYED) {
                hasDeployed = true;
            } else if (envStatus.getStatus() == RevisionState.UNDEPLOYED) {
                hasUndeployed = true;
            }
        }
        
        // Priority: DEPLOYED > UNDEPLOYED > DRAFT
        if (hasDeployed) {
            this.state = RevisionState.DEPLOYED;
        } else if (hasUndeployed) {
            this.state = RevisionState.UNDEPLOYED;
        } else {
            this.state = RevisionState.DRAFT;
        }
    }
}
