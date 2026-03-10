package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateApiRequest {

    @NotBlank(message = "API name is required")
    private String name;

    private String displayName;
}
