package de.homelabs.projects;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The micro frontend the service ships for the homelabs app: its descriptor and its
 * static modules are open (they carry no data), while every API call it makes still
 * needs a token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestSecurityConfig.class)
class MfeIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void describesTheMicroFrontendWithoutAToken() throws Exception {
        HttpResponse<String> response = get("/mfe/descriptor");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body())
                .contains("\"id\":\"projects\"")
                .contains("\"element\":\"hl-projects\"")
                .contains("\"entry\":\"projects.js\"")
                .contains("\"label\":{\"de\":\"Projekte\",\"en\":\"Projects\"}")
                .contains("\"icon\":\"folder\"")
                .contains("\"order\":10")
                .doesNotContain("roles")
                .doesNotContain("\"version\":\"dev\"");
    }

    @Test
    void servesTheModulesWithoutAToken() throws Exception {
        for (String path : new String[] { "/mfe/projects.js", "/mfe/lib.js", "/mfe/views/list.js",
                "/mfe/views/project.js", "/mfe/views/item.js" }) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type")).as(path)
                    .hasValueSatisfying(type -> assertThat(type).contains("javascript"));
        }
        assertThat(get("/mfe/projects.js").body()).contains("customElements.define('hl-projects'");
    }

    @Test
    void keepsTheApiBehindTheToken() throws Exception {
        assertThat(get("/api/projects").statusCode()).isEqualTo(401);
        assertThat(get("/mfe/../api/projects").statusCode()).isNotEqualTo(200);
    }
}
