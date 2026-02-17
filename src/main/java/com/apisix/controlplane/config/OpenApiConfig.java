package com.apisix.controlplane.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("APISIX Control Plane")
                        .version("1.0")
                        .description("API Gateway Control Plane for managing services, revisions, upstreams, and deployments"))
                .tags(List.of(
                        new Tag().name("Upstreams"),
                        new Tag().name("Services"),
                        new Tag().name("Service Revisions"),
                        new Tag().name("Services Overview")
                ));
    }
}
