package com.apisix.controlplane.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "environments")
@CompoundIndex(name = "org_env_name", def = "{'orgId': 1, 'name': 1}", unique = true)
public class Environment {

    @Id
    private String id;

    private String orgId;

    private String name; // e.g., "qa", "prod"

    private String description;

    private String apisixAdminUrl; // e.g., "http://localhost:9180"

    private boolean active;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

