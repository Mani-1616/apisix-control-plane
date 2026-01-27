package com.apisix.controlplane.entity;

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
import java.util.Map;

/**
 * Environment-specific upstream configuration
 * Upstreams are scoped to environments and created immediately in APISIX
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "upstreams")
@CompoundIndex(name = "env_name", def = "{'environmentId': 1, 'name': 1}", unique = true)
public class Upstream {

    @Id
    private String id;

    private String orgId; // For authorization checks
    
    /**
     * Environment ID - upstreams are now scoped to environments
     */
    private String environmentId;

    /**
     * Upstream name (e.g., "todo-api-backend")
     * Must be unique within environment (not organization)
     */
    private String name;

    /**
     * Description of this upstream
     */
    private String description;

    /**
     * Target URL (e.g., "https://qa-api.example.com")
     * Different per environment (qa-api vs prod-api)
     */
    private String targetUrl;

    /**
     * Load balancing type (roundrobin, chash, ewma, least_conn)
     */
    @Builder.Default
    private String type = "roundrobin";

    /**
     * Optional: Additional upstream configuration
     * (e.g., timeout, retries, health checks, pass_host)
     */
    private Map<String, Object> config;

    /**
     * Metadata for tracking
     */
    private Map<String, Object> metadata;

    /**
     * Indicates if this upstream is currently in use by any API revisions
     */
    @Builder.Default
    private boolean inUse = false;
    
    /**
     * APISIX upstream ID (generated and stored)
     */
    private String apisixId;
    
    /**
     * Status of upstream in APISIX
     */
    @Builder.Default
    private UpstreamStatus apisixStatus = UpstreamStatus.PENDING;

    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    /**
     * Last time this upstream was synced with APISIX
     */
    private LocalDateTime lastSyncedAt;
    
    public enum UpstreamStatus {
        PENDING,    // Not yet created in APISIX
        ACTIVE,     // Successfully created in APISIX
        FAILED,     // Failed to create in APISIX
        SYNCING     // Currently being created/updated
    }

    /**
     * Generate APISIX upstream ID
     * Format: cp-ups-{envHash}-{name}
     */
    public String generateApisixId() {
        if (apisixId != null) {
            return apisixId; // Return cached value
        }
        
        // Generate hash and ensure it's at least 8 characters (pad with zeros if needed)
        String envHash = Integer.toHexString(environmentId.hashCode());
        // Pad with leading zeros if necessary to ensure minimum 8 characters
        envHash = String.format("%8s", envHash).replace(' ', '0');
        // Take first 8 characters
        if (envHash.length() > 8) {
            envHash = envHash.substring(0, 8);
        }
        
        String sanitizedName = name.replaceAll("[^a-zA-Z0-9-_]", "-").toLowerCase();
        sanitizedName = sanitizedName.replaceAll("-+", "-").replaceAll("^-|-$", "");
        
        if (sanitizedName.length() > 40) {
            sanitizedName = sanitizedName.substring(0, 40);
        }
        
        String generatedId = String.format("cp-ups-%s-%s", envHash, sanitizedName);
        
        // Ensure max 64 chars for APISIX
        if (generatedId.length() > 64) {
            generatedId = generatedId.substring(0, 64);
        }
        generatedId = generatedId.replaceAll("-$", "");
        
        this.apisixId = generatedId;
        return generatedId;
    }
}

