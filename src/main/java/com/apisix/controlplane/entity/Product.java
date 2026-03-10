package com.apisix.controlplane.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "products", uniqueConstraints = {
    @UniqueConstraint(name = "uk_product_org_env_name", columnNames = {"org_id", "env_id", "name"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "env_id", nullable = false)
    private String envId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "display_name")
    private String displayName;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "product_apis",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "api_id")
    )
    @Builder.Default
    private List<Api> apis = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plugins", columnDefinition = "jsonb")
    private Map<String, Object> plugins;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
