package com.apisix.controlplane.apisix.model;

import com.apisix.controlplane.apisix.validation.ValidRouteSpec;
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
 * Strongly-typed representation of the APISIX Route resource body.
 * Matches the APISIX 3.14 Admin API schema (main.route).
 * <p>
 * Fields {@code id}, {@code create_time}, and {@code update_time} are excluded
 * because they are managed by APISIX itself.
 * <p>
 * {@code service_id} is typically null when stored in the DB and set at deploy time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ValidRouteSpec
public class RouteSpec {

    /** Route name (max 100 chars). */
    private String name;

    /** Description (max 256 chars). */
    private String desc;

    /** Key/value labels. */
    private Map<String, String> labels;

    /** URI path for this route. */
    private String uri;

    /** Allowed HTTP methods: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, CONNECT, TRACE, PURGE. */
    private List<String> methods;

    /** Single hostname (mutually exclusive with hosts). */
    private String host;

    /** Multiple hostnames (mutually exclusive with host). */
    private List<String> hosts;

    /** Single client IP filter (mutually exclusive with remote_addrs). */
    private String remoteAddr;

    /** Multiple client IP filters (mutually exclusive with remote_addr). */
    private List<String> remoteAddrs;

    /** APISIX vars matching rules. */
    private List<Object> vars;

    /** Lua filter function (starts with "function"). */
    private String filterFunc;

    /** Reference to an existing APISIX service (set at deploy time). */
    private String serviceId;

    /** Reference to an existing APISIX upstream. */
    private String upstreamId;

    /** Reference to a plugin config object. */
    private String pluginConfigId;

    /** Route-level APISIX plugins. */
    private Map<String, Object> plugins;

    /** Enable WebSocket support. */
    private Boolean enableWebsocket;

    /** Route status: 1 = enabled (default), 0 = disabled. */
    private Integer status;

    /** Route priority (higher = matched first, default 0). */
    private Integer priority;

    /** Connection/read/send timeout override. */
    private TimeoutConfig timeout;

    /** Lua script (mutually exclusive with plugins). */
    private String script;

    /** Script ID reference. */
    private String scriptId;
}
