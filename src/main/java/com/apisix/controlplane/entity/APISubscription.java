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

@Document(collection = "api-subscriptions")
@CompoundIndex(name = "org_dev_api_env", def = "{'orgId': 1, 'developerId': 1, 'apiName': 1, 'envId': 1}", unique = true)
@CompoundIndex(name = "org_env_dev", def = "{'orgId': 1, 'envId': 1, 'developerId': 1}")
@CompoundIndex(name = "org_dev", def = "{'orgId': 1, 'developerId': 1}")
@CompoundIndex(name = "apisix_consumer", def = "{'apisixConsumerId': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APISubscription {
    
    @Id
    private String id;
    
    private String orgId;
    
    private String envId;
    
    private String developerId;
    
    private String apisixConsumerId; // Consumer ID in APISIX
    
    private String apiName;
    
    private String serviceId; // APISIX service ID for the subscribed API
    
    private SubscriptionStatus status;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private String apiKey; // Generated API key for this subscription
}

