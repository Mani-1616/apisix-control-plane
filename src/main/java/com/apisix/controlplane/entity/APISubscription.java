package com.apisix.controlplane.entity;

import com.apisix.controlplane.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_subscriptions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_api_sub_org_dev_svc_env", columnNames = {"org_id", "developer_id", "service_id", "env_id"})
}, indexes = {
    @Index(name = "idx_api_sub_org_env_dev", columnList = "org_id, env_id, developer_id"),
    @Index(name = "idx_api_sub_org_dev", columnList = "org_id, developer_id"),
    @Index(name = "idx_api_sub_apisix_consumer", columnList = "apisix_consumer_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APISubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "env_id", nullable = false)
    private String envId;

    @Column(name = "developer_id", nullable = false)
    private String developerId;

    @Column(name = "apisix_consumer_id")
    private String apisixConsumerId;

    /** FK to ApiService. Replaces the former apiName field. */
    @Column(name = "service_id", nullable = false)
    private String serviceId;

    /** APISIX service ID used for the consumer-restriction whitelist. */
    @Column(name = "apisix_service_id")
    private String apisixServiceId;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "api_key")
    private String apiKey;
}
