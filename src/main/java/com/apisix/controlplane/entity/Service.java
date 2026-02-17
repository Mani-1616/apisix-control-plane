package com.apisix.controlplane.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Represents an API service identity within an organization.
 * Service revisions are versioned configurations of this service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "services", uniqueConstraints = {
    @UniqueConstraint(name = "uk_service_org_name", columnNames = {"org_id", "name"})
})
@EntityListeners(AuditingEntityListener.class)
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private String orgId;

    /** Immutable unique name within the organization. */
    @Column(nullable = false, updatable = false)
    private String name;


    @Column(name = "display_name")
    private String displayName;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
