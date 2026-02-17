package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSubscriptionRequest {

    @NotBlank(message = "Environment ID is required")
    private String envId;

    @NotBlank(message = "Developer ID is required")
    private String developerId;

    @NotBlank(message = "Service ID is required")
    private String serviceId;
}
