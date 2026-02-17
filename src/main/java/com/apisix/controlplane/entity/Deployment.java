package com.apisix.controlplane.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Represents an active deployment of a service revision in an environment.
 * Only active deployments exist -- on undeploy, the row is deleted.
 * Unique constraint (service_id, environment_id) enforces one active revision per service per environment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "deployments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_deployment_svc_env", columnNames = {"service_id", "environment_id"})
}, indexes = {
    @Index(name = "idx_deployment_env", columnList = "environment_id"),
    @Index(name = "idx_deployment_rev", columnList = "revision_id"),
    @Index(name = "idx_deployment_org", columnList = "org_id")
})
@EntityListeners(AuditingEntityListener.class)
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(name = "revision_id", nullable = false)
    private String revisionId;

    @Column(name = "environment_id", nullable = false)
    private String environmentId;

    @CreatedDate
    @Column(name = "deployed_at")
    private LocalDateTime deployedAt;
}
