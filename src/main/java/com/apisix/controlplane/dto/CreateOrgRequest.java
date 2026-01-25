package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrgRequest {
    @NotBlank(message = "Organization name is required")
    private String name;

    private String description;
}

