package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProductSubscriptionRequest {
    
    @NotBlank(message = "Developer ID is required")
    private String developerId;
}

