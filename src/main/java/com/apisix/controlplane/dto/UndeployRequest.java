package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UndeployRequest {

    /**
     * Environment ID to undeploy from
     */
    @NotBlank(message = "Environment ID is required")
    private String environmentId;

}
