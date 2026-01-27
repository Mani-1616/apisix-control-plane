package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSubscriptionRequest {
    
    @NotBlank(message = "Environment ID is required")
    private String envId;
    
    @NotBlank(message = "Developer ID is required")
    private String developerId;
    
    @NotBlank(message = "API name is required")
    private String apiName;
}

