package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProductSubscriptionRequest {
    
    @NotBlank(message = "Developer ID is required")
    private String developerId;
    
    @NotBlank(message = "Environment ID is required")
    private String envId;
    
    @NotBlank(message = "Product ID is required")
    private String productId;
}

