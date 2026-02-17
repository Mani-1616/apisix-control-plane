package com.apisix.controlplane.entity;

import com.apisix.controlplane.apisix.model.UpstreamSpec;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Environment-specific upstream configuration.
 * Control-plane metadata is stored as columns; the APISIX upstream body is in the specification JSONB column.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "upstreams", uniqueConstraints = {
    @UniqueConstraint(name = "uk_upstream_env_name", columnNames = {"environment_id", "name"})
})
@EntityListeners(AuditingEntityListener.class)
public class Upstream {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "org_id")
    private String orgId;

    @Column(name = "environment_id")
    private String environmentId;

    @Column(nullable = false)
    private String name;

    /**
     * Full APISIX upstream specification. Stored as typed JSONB.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private UpstreamSpec specification;

    @Column(name = "apisix_id")
    private String apisixId;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Generate APISIX upstream ID.
     * Format: cp-ups-{envHash}-{name}
     */
    public String generateApisixId() {
        if (apisixId != null) {
            return apisixId;
        }

        String envHash = Integer.toHexString(environmentId.hashCode());
        envHash = String.format("%8s", envHash).replace(' ', '0');
        if (envHash.length() > 8) {
            envHash = envHash.substring(0, 8);
        }

        String sanitizedName = name.replaceAll("[^a-zA-Z0-9-_]", "-").toLowerCase();
        sanitizedName = sanitizedName.replaceAll("-+", "-").replaceAll("^-|-$", "");

        if (sanitizedName.length() > 40) {
            sanitizedName = sanitizedName.substring(0, 40);
        }

        String generatedId = String.format("cp-ups-%s-%s", envHash, sanitizedName);

        if (generatedId.length() > 64) {
            generatedId = generatedId.substring(0, 64);
        }
        generatedId = generatedId.replaceAll("-$", "");

        this.apisixId = generatedId;
        return generatedId;
    }
}
