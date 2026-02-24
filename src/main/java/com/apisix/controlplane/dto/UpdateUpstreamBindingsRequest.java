package com.apisix.controlplane.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpdateUpstreamBindingsRequest {

    @NotEmpty(message = "At least one upstream binding is required")
    private List<@Valid EnvironmentUpstreamMapping> environmentUpstreams;
}
