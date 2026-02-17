package com.apisix.controlplane.apisix.model;

import com.apisix.controlplane.apisix.validation.ValidServiceSpec;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Strongly-typed representation of the APISIX Service resource body.
 * Matches the APISIX 3.14 Admin API schema (main.service).
 * <p>
 * Fields {@code id}, {@code create_time}, and {@code update_time} are excluded
 * because they are managed by APISIX itself.
 * <p>
 * {@code upstream_id} is typically null when stored in the DB and set at deploy time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ValidServiceSpec
public class ServiceSpec {

    /** Service name (max 100 chars). */
    private String name;

    /** Description (max 256 chars). */
    private String desc;

    /** Key/value labels. */
    private Map<String, String> labels;

    /** Reference to an existing APISIX upstream (set at deploy time). */
    private String upstreamId;

    /** Service-level APISIX plugins. */
    private Map<String, Object> plugins;

    /** Enable WebSocket support. */
    private Boolean enableWebsocket;

    /** Allowed hostnames for this service. */
    private List<String> hosts;

    /** Lua script (mutually exclusive with plugins). */
    private String script;
}
