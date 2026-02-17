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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "environments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_env_org_name", columnNames = {"org_id", "name"})
})
@EntityListeners(AuditingEntityListener.class)
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(nullable = false)
    private String name; // e.g., "qa", "prod"

    private String description;

    @Column(name = "apisix_admin_url")
    private String apisixAdminUrl; // e.g., "http://localhost:9180"

    private boolean active;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
