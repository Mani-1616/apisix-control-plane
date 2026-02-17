package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateServiceRequest {

    @NotBlank(message = "Service name is required")
    private String name;

    private String displayName;
}
