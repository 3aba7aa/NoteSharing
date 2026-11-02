package org.example.notesharing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the OpenAPI document that Swagger UI renders.
 *
 * <p>This exists because the document was being generated successfully while being useless:
 * it declared no security scheme, so the UI showed no Authorize button and every protected
 * "Try it out" came back 401. A page that loads is not evidence that the tool works.
 *
 * <p>Asserts over the served JSON rather than over the annotations, because the annotations
 * are only a means -- springdoc can drop or misplace them, and the output is the only thing
 * a user actually depends on.
 *
 * <p>Uses the JDK's HTTP client against the real port so the request travels the same route
 * a browser takes, including the security filter chain. MockMvc would bypass that chain and
 * so could not catch a document being served to an unauthenticated caller.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiDocumentTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Follows redirects, because a browser does. {@code HttpClient.newHttpClient()} defaults
     * to {@code Redirect.NEVER}, which made {@code /swagger-ui.html}'s normal hop to
     * {@code /swagger-ui/index.html} look like a failure when it is springdoc working
     * correctly.
     */
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build();

    @LocalServerPort
    int port;

    private JsonNode document;

    @BeforeEach
    void fetchDocument() throws Exception {
        document = get("/v3/api-docs");
        assertThat(document).as("the OpenAPI document must be parseable JSON").isNotNull();
    }

    @Test
    void declaresABearerSecurityScheme() {
        JsonNode schemes = document.path("components").path("securitySchemes");
        assertThat(schemes.has("bearerAuth"))
            .as("Swagger UI renders no Authorize button without a security scheme")
            .isTrue();
        assertThat(schemes.path("bearerAuth").path("type").asText()).isEqualTo("http");
        assertThat(schemes.path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
    }

    @Test
    void theGlobalRequirementNamesTheBearerScheme() {
        // Security is declared once at the document root rather than on each of the 26
        // protected operations. Per the OpenAPI spec an operation inherits the root
        // requirement when it declares none, so this covers all of them -- and repeating it
        // per operation would be a way to forget one.
        JsonNode security = document.path("security");
        assertThat(security.isArray() && !security.isEmpty())
            .as("without a root requirement no operation is marked as needing a token")
            .isTrue();
        assertThat(security.get(0).has("bearerAuth"))
            .as("the requirement must name the declared scheme")
            .isTrue();
    }

    @Test
    void publicAuthOperationsOverrideTheGlobalRequirement() {
        // An explicit empty array is the opt-out. Register and login are called without a
        // token by definition; without this they inherit the root requirement and the UI
        // would ask for a token in order to obtain a token.
        for (String path : List.of("/api/v1/auth/register", "/api/v1/auth/login",
                "/api/v1/auth/refresh")) {
            JsonNode security = document.path("paths").path(path).path("post").path("security");
            assertThat(security.isArray())
                .as("%s needs an explicit empty array to override the global requirement", path)
                .isTrue();
            assertThat(security.isEmpty())
                .as("%s is public and must not demand a bearer token", path)
                .isTrue();
        }
    }

    @Test
    void securedEndpointsInheritTheRequirement() {
        // Absent rather than empty: these must inherit the root requirement, not override it.
        // An empty array here would mean "public", which would be wrong and would let a user
        // call them from the UI and get a 401.
        for (String path : List.of("/api/v1/notes", "/api/v1/notifications",
                "/api/v1/users/me")) {
            JsonNode security = document.path("paths").path(path).path("get").path("security");
            assertThat(security.isMissingNode() || security.isEmpty())
                .as("GET %s needs a token and must inherit the global requirement", path)
                .isTrue();
        }
    }

    @Test
    void carriesARealTitleAndVersion() {
        JsonNode info = document.path("info");
        assertThat(info.path("title").asText())
            .as("the placeholder title makes a working API look broken")
            .isEqualTo("NoteSharing API");
        assertThat(info.path("version").asText()).isNotEqualTo("v0");
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        HttpResponse<String> response = HTTP.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/swagger-ui.html"))
                .GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.uri().getPath())
            .as("swagger-ui.html should land on the UI itself")
            .isEqualTo("/swagger-ui/index.html");
        assertThat(response.body()).contains("swagger-ui");
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
            .as("%s must be reachable without a token", path)
            .isEqualTo(200);
        return JSON.readTree(response.body());
    }
}