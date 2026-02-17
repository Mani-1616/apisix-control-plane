package com.apisix.controlplane.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "products", uniqueConstraints = {
    @UniqueConstraint(name = "uk_product_org_name", columnNames = {"org_id", "name"})
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

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "display_name")
    private String displayName;

    /** List of ApiService IDs that are part of this product. Replaces the former apiNames field. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "service_ids", columnDefinition = "jsonb")
    private List<String> serviceIds;

    @Column(name = "plugin_config", columnDefinition = "text")
    private String pluginConfig;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
