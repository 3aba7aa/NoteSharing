package org.example.notesharing.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API for Swagger UI.
 *
 * <p>The security scheme is the load-bearing part. Without it springdoc still generated a
 * perfectly valid document -- 29 paths, every schema -- but declared no way to authenticate,
 * so Swagger UI rendered no Authorize button and every protected "Try it out" came back 401.
 * A green document is not the same as a usable tool.
 *
 * <p>Security is declared once, globally, because almost every endpoint needs a token. The
 * three public endpoints in {@code AuthController} opt out with an empty
 * {@code @SecurityRequirements}, which is less error-prone than annotating all 26 others and
 * forgetting one.
 */
@Configuration
@SecurityScheme(
    name = OpenApiConfig.BEARER,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Access token from POST /api/v1/auth/login. Paste the token only, "
        + "without the \"Bearer \" prefix.")
@OpenAPIDefinition(
    info = @Info(
        title = "NoteSharing API",
        version = "v1",
        description = "Notes, sharing, social relationships and notifications."),
    security = @SecurityRequirement(name = OpenApiConfig.BEARER))
public class OpenApiConfig {

    /** Must match the scheme name above; Swagger UI keys its Authorize dialog on it. */
    public static final String BEARER = "bearerAuth";
}