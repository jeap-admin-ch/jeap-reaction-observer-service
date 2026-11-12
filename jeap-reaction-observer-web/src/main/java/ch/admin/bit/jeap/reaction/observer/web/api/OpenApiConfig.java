package ch.admin.bit.jeap.reaction.observer.web.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "jEAP Reaction Observer Service",
                description = "The jEAP Reaction Observation Service records and aggregates triggers, actions, and reactions from events and makes the data available via a REST API."
        ),
        security = {@SecurityRequirement(name = "bearerAuth")}
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@Configuration
public class OpenApiConfig {
    static final String GROUP_V1 = "reaction-observer-service-api";
    static final String GROUP_V2 = "reaction-observer-service-api-v2";
    private static final String PATHS_V2 = "/api/v2/**";
    /**
     * The version of the API a group documents. The OpenAPI specification requires one in every document, and a
     * client generator rejects a document without.
     */
    static final String VERSION_V1 = "1.0.0";
    static final String VERSION_V2 = "2.0.0";

    /**
     * The API v1 and the endpoints that have no version (names, statistics, management), under the name the group
     * always had.
     */
    @Bean
    GroupedOpenApi externalApi() {
        return GroupedOpenApi.builder()
                .group(GROUP_V1)
                .pathsToMatch("/api/**")
                .pathsToExclude(PATHS_V2)
                .packagesToScan(this.getClass().getPackageName())
                .addOpenApiCustomizer(openApi -> openApi.getInfo().setVersion(VERSION_V1))
                .build();
    }

    /** The API v2, in a group of its own. */
    @Bean
    GroupedOpenApi externalApiV2() {
        return GroupedOpenApi.builder()
                .group(GROUP_V2)
                .pathsToMatch(PATHS_V2)
                .packagesToScan(this.getClass().getPackageName())
                .addOpenApiCustomizer(openApi -> openApi.getInfo().setVersion(VERSION_V2))
                .build();
    }
}
