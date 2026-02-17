package com.apisix.controlplane.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "developers", uniqueConstraints = {
    @UniqueConstraint(name = "uk_developer_org_email", columnNames = {"org_id", "email"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Developer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(nullable = false)
    private String email;

    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
