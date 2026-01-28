package com.apisix.controlplane.entity;

import com.apisix.controlplane.enums.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "product-subscriptions")
@CompoundIndex(name = "org_dev_prod_env", def = "{'orgId': 1, 'developerId': 1, 'productId': 1, 'envId': 1}", unique = true)
@CompoundIndex(name = "org_env", def = "{'orgId': 1, 'envId': 1}")
@CompoundIndex(name = "org_dev_env", def = "{'orgId': 1, 'developerId': 1, 'envId': 1}")
@CompoundIndex(name = "consumer_id", def = "{'consumerId': 1}")
@CompoundIndex(name = "consumer_group", def = "{'consumerGroupId': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSubscription {
    
    @Id
    private String id;
    
    private String orgId;
    
    private String envId;
    
    private String developerId;
    
    private String productId;
    
    /**
     * APISIX consumer ID for this product subscription
     * Format: prod-{productName}-{envHash}-{developerHash}
     */
    private String consumerId;
    
    /**
     * APISIX consumer group ID
     * Format: grp-{productName}-{envHash}
     */
    private String consumerGroupId;
    
    /**
     * Unique API key for this product subscription
     */
    private String apiKey;
    
    private SubscriptionStatus status; // PENDING, ACTIVE, REVOKED
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}

