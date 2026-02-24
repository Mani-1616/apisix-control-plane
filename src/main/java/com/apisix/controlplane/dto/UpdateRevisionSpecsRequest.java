package com.apisix.controlplane.dto;

import com.apisix.controlplane.apisix.model.RouteSpec;
import com.apisix.controlplane.apisix.model.ServiceSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpdateRevisionSpecsRequest {

    @Valid
    private ServiceSpec serviceSpecification;

    @NotEmpty(message = "At least one route specification is required")
    private List<@Valid RouteSpec> routeSpecifications;
}
