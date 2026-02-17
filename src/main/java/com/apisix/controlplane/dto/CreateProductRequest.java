package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotBlank(message = "Display name is required")
    private String displayName;

    @NotEmpty(message = "At least one service must be included in the product")
    private List<String> serviceIds;

    /**
     * Optional plugin configuration (JSON string) to be applied at consumer group level.
     */
    private String pluginConfig;
}
