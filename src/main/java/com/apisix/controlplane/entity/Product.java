package com.apisix.controlplane.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "products")
@CompoundIndex(name = "org_name", def = "{'orgId': 1, 'name': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    
    @Id
    private String id;
    
    private String orgId;
    
    private String name;
    
    private String description;
    
    private String displayName;
    
    /**
     * List of API names that are part of this product
     */
    private List<String> apiNames;
    
    /**
     * Plugin configuration to be applied to consumer group in APISIX (stored as JSON string)
     */
    private String pluginConfig;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}

