package de.homelabs.projects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot smoke test: the context boots against a real Postgres, Flyway migrates, and the
 * REST API answers behind the OAuth 2.0 resource server — the open liveness probe, the
 * authenticated surface, the snake_case wire vocabulary, owner provenance and the
 * activity trace of a mutation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestSecurityConfig.class)
class ProjectsBootIT extends ApiTestSupport {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void servesTheOpenHealthProbe() throws Exception {
        Response health = get("/health", null);

        assertThat(health.status()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"").contains("homelabs-projects");
    }

    @Test
    void requiresABearerTokenUnderTheApi() throws Exception {
        assertThat(get("/api/projects", null).status()).isEqualTo(401);
        assertThat(post("/api/projects", """
                {"name":"unauthorized"}""", null).status()).isEqualTo(401);
        assertThat(get("/api/labels", null).status()).isEqualTo(401);
    }

    @Test
    void rejectsTokensForAnotherResource() throws Exception {
        String foreign = token("it-foreign-sub", TestSecurityConfig.FOREIGN_AUDIENCE, null);

        assertThat(get("/api/projects", foreign).status()).isEqualTo(401);
    }

    @Test
    void createsAProjectWithSnakeCaseFieldsAndOwnerProvenance() throws Exception {
        String token = bearerToken("it-boot-sub", "Boot User");

        Response created = post("/api/projects", """
                {"name":"Boot Smoke","short_code":"BOOTS","description":"ein Projekt"}""", token);

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body())
                .contains("\"short_code\":\"BOOTS\"")
                .contains("\"name\":\"Boot Smoke\"")
                .contains("\"status\":\"active\"")
                .contains("\"owner_sub\":\"it-boot-sub\"")
                .contains("\"owner_name\":\"Boot User\"")
                .contains("\"description_html\":\"<p>ein Projekt</p>\"")
                .contains("\"description_text\":\"ein Projekt\"")
                .contains("\"created_at\":");

        String ownerSub = jdbc.sql("SELECT owner_sub FROM project WHERE short_code = 'BOOTS'")
                .query(String.class)
                .single();
        assertThat(ownerSub).isEqualTo("it-boot-sub");

        Integer createdEvents = jdbc.sql("SELECT count(*) FROM activity WHERE entity_id = "
                        + "(SELECT id FROM project WHERE short_code = 'BOOTS') AND event_type = 'created'")
                .query(Integer.class)
                .single();
        assertThat(createdEvents).isEqualTo(1);
    }

    @Test
    void recordsTheHarnessIdentityFromTheSourceHeaderOnly() throws Exception {
        String token = bearerToken("it-source-sub");

        post("/api/projects", """
                {"name":"Source Marker","short_code":"SRC"}""", token, "it-boot-client");

        String source = jdbc.sql("SELECT source FROM activity WHERE entity_id = "
                        + "(SELECT id FROM project WHERE short_code = 'SRC') AND event_type = 'created'")
                .query(String.class)
                .single();
        assertThat(source).isEqualTo("it-boot-client");

        post("/api/projects", """
                {"name":"Direct Caller","short_code":"DIRECT"}""", token);

        String noSource = jdbc.sql("SELECT source FROM activity WHERE entity_id = "
                        + "(SELECT id FROM project WHERE short_code = 'DIRECT') AND event_type = 'created'")
                .query(String.class)
                .optional()
                .orElse(null);
        assertThat(noSource).isNull();
    }

    @Test
    void answersValidationProblemsWithFieldMessages() throws Exception {
        String token = bearerToken("it-validation-sub");

        Response noName = post("/api/projects", """
                {"short_code":"NONAME"}""", token);
        assertThat(noName.status()).isEqualTo(400);
        assertThat(noName.body()).contains("name");

        Response badCode = post("/api/projects", """
                {"name":"Format","short_code":"toolongcode"}""", token);
        assertThat(badCode.status()).isEqualTo(400);
        assertThat(badCode.body()).contains("short_code");
    }
}
