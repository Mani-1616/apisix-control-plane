package com.apisix.controlplane.apisix.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * APISIX health check configuration for upstreams.
 * <p>
 * Note: {@link HealthyConfig} and {@link UnhealthyConfig} are shared between
 * active and passive checks. The {@code interval} field is only recognized by
 * APISIX for <b>active</b> health checks; it is silently ignored when set
 * under passive checks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthCheck {

    private ActiveHealthCheck active;
    private PassiveHealthCheck passive;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ActiveHealthCheck {
        private String type;                    // http, https, tcp
        private Double timeout;
        private Integer concurrency;
        private String httpPath;
        private Boolean httpsVerifyCertificate;
        private Integer port;
        private String host;
        private List<String> reqHeaders;
        private HealthyConfig healthy;
        private UnhealthyConfig unhealthy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PassiveHealthCheck {
        private String type;                    // http, https, tcp
        private HealthyConfig healthy;
        private UnhealthyConfig unhealthy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HealthyConfig {
        /** Check interval in seconds. Only applies to active health checks; ignored for passive. */
        private Integer interval;
        private List<Integer> httpStatuses;
        private Integer successes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UnhealthyConfig {
        /** Check interval in seconds. Only applies to active health checks; ignored for passive. */
        private Integer interval;
        private List<Integer> httpStatuses;
        private Integer httpFailures;
        private Integer tcpFailures;
        private Integer timeouts;
    }
}
