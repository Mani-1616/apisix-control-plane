package com.apisix.controlplane.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps a service revision to an upstream for a specific environment.
 * This is the single source of truth for which upstream a revision uses in each environment.
 * Created during revision setup, copied on clone, mutable while revision is in INACTIVE state.
 * Also updated at deploy time if the user provides an upstream override.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "upstream_bindings", uniqueConstraints = {
    @UniqueConstraint(name = "uk_upstream_binding", columnNames = {"revision_id", "environment_id"})
}, indexes = {
    @Index(name = "idx_upstream_binding_rev", columnList = "revision_id"),
    @Index(name = "idx_upstream_binding_org", columnList = "org_id")
})
public class UpstreamBinding {

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

    @Column(name = "upstream_id", nullable = false)
    private String upstreamId;
}
