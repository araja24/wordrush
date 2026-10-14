package dev.wordrush.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html: "Authorize" with the token from POST /api/v1/auth/guest. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(title = "WordRush API", version = "v1",
                description = "REST API for WordRush. Real-time gameplay runs over STOMP at /ws."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfig {
}
