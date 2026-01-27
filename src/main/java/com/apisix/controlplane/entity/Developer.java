package com.apisix.controlplane.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "developers")
@CompoundIndex(name = "org_email", def = "{'orgId': 1, 'email': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Developer {
    
    @Id
    private String id;
    
    private String orgId;
    
    private String email;
    
    private String name;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}

