package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateEnvironmentRequest {
    @NotBlank(message = "Environment name is required")
    private String name;

    private String description;

    @NotBlank(message = "APISIX Admin URL is required")
    private String apisixAdminUrl;

    private boolean active = true;
}

