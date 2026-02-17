package com.apisix.controlplane.apisix.validation;

import com.apisix.controlplane.apisix.model.RouteSpec;
import com.apisix.controlplane.apisix.model.ServiceSpec;
import com.apisix.controlplane.apisix.model.UpstreamSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Validates APISIX spec objects (RouteSpec, UpstreamSpec, ServiceSpec) against
 * the official APISIX 3.14 JSON Schema definitions.
 * <p>
 * Schemas are extracted at startup from {@code classpath:/control_plane_schema.json},
 * which contains the full APISIX schema under {@code main.route}, {@code main.upstream},
 * and {@code main.service}.
 * <p>
 * Validation converts the spec to a {@link JsonNode} (respecting the Jackson
 * snake_case naming on the model classes) and runs it through the schema.
 */
@Component
@Slf4j
public class ApisixSchemaValidator {

    private final JsonSchema routeSchema;
    private final JsonSchema upstreamSchema;
    private final JsonSchema serviceSchema;
    private final ObjectMapper objectMapper;

    public ApisixSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

        JsonNode root = loadControlPlaneSchema(objectMapper);

        JsonNode routeNode    = root.path("main").path("route");
        JsonNode upstreamNode = root.path("main").path("upstream");
        JsonNode serviceNode  = root.path("main").path("service");

        if (routeNode.isMissingNode() || upstreamNode.isMissingNode() || serviceNode.isMissingNode()) {
            throw new IllegalStateException(
                    "control_plane_schema.json is missing one or more required sub-schemas: main.route, main.upstream, main.service");
        }

        this.routeSchema    = factory.getSchema(routeNode);
        this.upstreamSchema = factory.getSchema(upstreamNode);
        this.serviceSchema  = factory.getSchema(serviceNode);

        log.info("APISIX JSON Schemas loaded successfully from control_plane_schema.json (route, upstream, service)");
    }

    /**
     * Validate a {@link RouteSpec} against the APISIX route schema.
     *
     * @return set of validation errors (empty if valid)
     */
    public Set<ValidationMessage> validateRoute(RouteSpec spec) {
        return validate(routeSchema, spec);
    }

    /**
     * Validate an {@link UpstreamSpec} against the APISIX upstream schema.
     *
     * @return set of validation errors (empty if valid)
     */
    public Set<ValidationMessage> validateUpstream(UpstreamSpec spec) {
        return validate(upstreamSchema, spec);
    }

    /**
     * Validate a {@link ServiceSpec} against the APISIX service schema.
     *
     * @return set of validation errors (empty if valid)
     */
    public Set<ValidationMessage> validateService(ServiceSpec spec) {
        return validate(serviceSchema, spec);
    }

    private Set<ValidationMessage> validate(JsonSchema schema, Object spec) {
        JsonNode node = objectMapper.convertValue(spec, JsonNode.class);
        return schema.validate(node);
    }

    private static JsonNode loadControlPlaneSchema(ObjectMapper mapper) {
        try (InputStream is = ApisixSchemaValidator.class.getResourceAsStream("/control_plane_schema.json")) {
            if (is == null) {
                throw new IllegalStateException(
                        "Schema resource not found on classpath: /control_plane_schema.json");
            }
            return mapper.readTree(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse control_plane_schema.json", e);
        }
    }
}
