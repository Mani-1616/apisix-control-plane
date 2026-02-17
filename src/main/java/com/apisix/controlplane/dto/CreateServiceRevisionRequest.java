package com.apisix.controlplane.dto;

import com.apisix.controlplane.apisix.model.RouteSpec;
import com.apisix.controlplane.apisix.model.ServiceSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateServiceRevisionRequest {

    /**
     * Environment-to-upstream mappings.
     * User can select a different upstream for each environment.
     */
    private List<@Valid EnvironmentUpstreamMapping> environmentUpstreams;

    /** APISIX service specification (plugins, enable_websocket, hosts, etc.). */
    @Valid
    private ServiceSpec serviceSpecification;

    /** APISIX route specifications. At least one route is required. */
    @NotEmpty(message = "At least one route specification is required")
    private List<@Valid RouteSpec> routeSpecifications;
}
