package com.apisix.controlplane.apisix.model;

import com.apisix.controlplane.apisix.validation.ValidUpstreamSpec;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Strongly-typed representation of the APISIX Upstream resource body.
 * Matches the APISIX 3.14 Admin API schema (main.upstream).
 * <p>
 * Fields {@code id}, {@code create_time}, and {@code update_time} are excluded
 * because they are managed by APISIX itself (id is set via the URL path).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ValidUpstreamSpec
public class UpstreamSpec {

    /** Upstream name (max 100 chars). */
    private String name;

    /** Description (max 256 chars). */
    private String desc;

    /** Key/value labels for categorization. */
    private Map<String, String> labels;

    /** Load-balancing algorithm: roundrobin (default), chash, ewma, least_conn. */
    private String type;

    /** Hash key source for consistent hashing: vars, header, cookie, consumer, vars_combinations. */
    private String hashOn;

    /** The key of chash for dynamic load balancing. */
    private String key;

    /** Upstream scheme: http (default), https, grpc, grpcs, tcp, tls, udp, kafka. */
    private String scheme;

    /** Host passing mode: pass (default), node, rewrite. */
    private String passHost;

    /** Upstream host header value (used when pass_host = rewrite). */
    private String upstreamHost;

    /**
     * Service nodes. Polymorphic:
     * <ul>
     *   <li>Map form: {@code {"host:port": weight}}</li>
     *   <li>Array form: {@code [{"host":"...","port":...,"weight":...}]}</li>
     * </ul>
     */
    private Object nodes;

    /** Service name for service discovery. */
    private String serviceName;

    /** Discovery type (e.g. "nacos", "dns", "consul"). */
    private String discoveryType;

    /** Discovery arguments. */
    private DiscoveryArgs discoveryArgs;

    /** Number of retries on failure. */
    private Integer retries;

    /** Retry timeout in seconds (0 = no limit). */
    private Double retryTimeout;

    /** Connection/read/send timeout. */
    private TimeoutConfig timeout;

    /** Keepalive connection pool settings. */
    private KeepalivePool keepalivePool;

    /** Health check configuration. */
    private HealthCheck checks;

    /** TLS settings for upstream connections. */
    private UpstreamTls tls;
}
