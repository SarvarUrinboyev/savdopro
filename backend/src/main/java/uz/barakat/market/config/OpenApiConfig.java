package uz.barakat.market.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger config. Exposes a dedicated <b>public</b> doc group for the
 * external Open API ({@code /api/v1/**}) documented with an API-key (bearer)
 * security scheme. Swagger UI is already public in {@code SecurityConfig}.
 * Browse at {@code /swagger-ui.html?urls.primaryName=public}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public OpenAPI savdoproOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SavdoPRO Open API")
                        .version("v1")
                        .description("External read API + webhooks for integrations "
                                + "(web shop, marketplace, accounting, Telegram bot). "
                                + "Authenticate with an API key: Authorization: Bearer sk_live_…"))
                .components(new Components().addSecuritySchemes("ApiKey",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("Per-integration API key (sk_live_…).")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKey"));
    }
}
