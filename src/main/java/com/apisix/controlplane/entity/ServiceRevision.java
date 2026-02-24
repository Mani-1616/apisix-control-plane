package com.apisix.controlplane.entity;

import com.apisix.controlplane.apisix.model.RouteSpec;
import com.apisix.controlplane.apisix.model.ServiceSpec;
import com.apisix.controlplane.enums.RevisionState;
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
import java.util.List;

/**
 * A versioned revision of an {@link Service}.
 * Each revision contains APISIX service and route specifications stored as typed JSONB.
 * Deployment status is tracked via the {@link Deployment} entity.
 * Upstream bindings per environment are tracked via the {@link UpstreamBinding} entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "service_revisions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_service_revision_svc_num", columnNames = {"service_id", "revision_number"})
}, indexes = {
    @Index(name = "idx_service_revision_org", columnList = "org_id")
})
@EntityListeners(AuditingEntityListener.class)
public class ServiceRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private RevisionState state = RevisionState.INACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "service_specification", columnDefinition = "jsonb")
    private ServiceSpec serviceSpecification;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_specifications", columnDefinition = "jsonb")
    private List<RouteSpec> routeSpecifications;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
