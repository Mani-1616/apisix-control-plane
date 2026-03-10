package com.apisix.controlplane.dto;

import com.apisix.controlplane.entity.Product;
import com.apisix.controlplane.entity.Api;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private String id;
    private String orgId;
    private String envId;
    private String name;
    private String description;
    private String displayName;
    private List<Api> apis;

    @Schema(description = "Plugins configuration")
    private Map<String, Object> plugins;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse fromEntity(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .orgId(product.getOrgId())
                .envId(product.getEnvId())
                .name(product.getName())
                .description(product.getDescription())
                .displayName(product.getDisplayName())
                .apis(product.getApis())
                .plugins(product.getPlugins())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
