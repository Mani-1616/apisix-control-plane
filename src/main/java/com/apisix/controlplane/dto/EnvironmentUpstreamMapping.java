package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentUpstreamMapping {

    @NotBlank(message = "Environment ID is required")
    private String environmentId;

    @NotBlank(message = "Upstream ID is required")
    private String upstreamId;
}
