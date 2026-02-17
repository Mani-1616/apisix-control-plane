package com.apisix.controlplane.entity;

import com.apisix.controlplane.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_subscriptions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_prod_sub_org_dev_prod_env", columnNames = {"org_id", "developer_id", "product_id", "env_id"})
}, indexes = {
    @Index(name = "idx_prod_sub_org_env", columnList = "org_id, env_id"),
    @Index(name = "idx_prod_sub_org_dev_env", columnList = "org_id, developer_id, env_id"),
    @Index(name = "idx_prod_sub_consumer_id", columnList = "consumer_id"),
    @Index(name = "idx_prod_sub_consumer_group", columnList = "consumer_group_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "env_id", nullable = false)
    private String envId;

    @Column(name = "developer_id", nullable = false)
    private String developerId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    /**
     * APISIX consumer ID for this product subscription
     * Format: prod-{productName}-{envHash}-{developerHash}
     */
    @Column(name = "consumer_id")
    private String consumerId;

    /**
     * APISIX consumer group ID
     * Format: grp-{productName}-{envHash}
     */
    @Column(name = "consumer_group_id")
    private String consumerGroupId;

    /**
     * Unique API key for this product subscription
     */
    @Column(name = "api_key")
    private String apiKey;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status; // PENDING, ACTIVE, REVOKED

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
