package com.apisix.controlplane.dto;

import com.apisix.controlplane.apisix.model.UpstreamSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUpstreamRequest {

    @NotBlank(message = "Upstream name is required")
    private String name;

    @NotNull(message = "Upstream specification is required")
    @Valid
    private UpstreamSpec specification;
}
