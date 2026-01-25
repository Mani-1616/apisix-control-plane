package com.apisix.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateApiRequest {
    @NotBlank(message = "API name is required")
    private String name;

    private String description;

    /**
     * Map of environment ID to upstream ID
     * User can select different upstream for each environment
     * Not all environments need to be specified during creation
     * Key: environmentId, Value: upstreamId
     */
    private Map<String, String> environmentUpstreams;

    private ServiceConfigDto serviceConfig;

    @NotEmpty(message = "At least one route is required")
    private List<RouteConfigDto> routes;

    @Data
    public static class ServiceConfigDto {
        private Map<String, Object> plugins;
        private Map<String, Object> metadata;
    }

    @Data
    public static class RouteConfigDto {
        @NotBlank(message = "Route name is required")
        private String name;
        @NotEmpty(message = "At least one HTTP method is required")
        private List<String> methods;
        @NotEmpty(message = "At least one URI is required")
        private List<String> uris;
        private Map<String, Object> plugins;
        private Map<String, Object> metadata;
    }
    
    /**
     * DTO for creating environment-specific upstreams
     * Now used only by the separate upstream creation endpoint
     */
    @Data
    public static class UpstreamConfigDto {
        @NotBlank(message = "Upstream name is required")
        private String name;
        
        @NotBlank(message = "Target URL is required")
        private String targetUrl;
        
        private String description;
        private String type;
        private Map<String, Object> config;
    }
}
