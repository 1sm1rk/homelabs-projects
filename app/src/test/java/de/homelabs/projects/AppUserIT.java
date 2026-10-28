package de.homelabs.projects;

import java.util.Map;

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
 * The local user directory: every authenticated request upserts the principal
 * ({@code sub} plus the {@code preferred_username} snapshot), and display names of
 * assignees and authors are resolved from that table — no Keycloak admin API involved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestSecurityConfig.class)
class AppUserIT extends ApiTestSupport {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void upsertsThePrincipalOnEveryAuthenticatedRequest() throws Exception {
        String token = bearerToken("it-user-sub", "sm1rk");

        assertThat(get("/api/projects", token).status()).isEqualTo(200);

        Map<String, Object> seen = jdbc.sql(
                        "SELECT username, first_seen_at, last_seen_at FROM app_user WHERE sub = 'it-user-sub'")
                .query()
                .singleRow();
        assertThat(seen.get("username")).isEqualTo("sm1rk");

        // the name snapshot follows the token on the next call
        get("/api/projects", bearerToken("it-user-sub", "sm1rk-renamed"));

        String renamed = jdbc.sql("SELECT username FROM app_user WHERE sub = 'it-user-sub'")
                .query(String.class)
                .single();
        assertThat(renamed).isEqualTo("sm1rk-renamed");

        Integer rows = jdbc.sql("SELECT count(*) FROM app_user WHERE sub = 'it-user-sub'")
                .query(Integer.class)
                .single();
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void resolvesTheAssigneeDisplayNameOnceTheAssigneeHasCalled() throws Exception {
        String creator = bearerToken("it-owner-sub");
        post("/api/projects", """
                {"name":"Zuordnung","short_code":"ZUORD"}""", creator);
        post("/api/tasks", """
                {"project":"ZUORD","title":"Zugewiesene Aufgabe","assignee_sub":"it-assignee-sub"}""", creator);

        // never seen: the subject itself is the fallback
        assertThat(get("/api/tasks/ZUORD-1", creator).body()).contains("\"assignee_name\":\"it-assignee-sub\"");

        // the assignee makes one call — now the directory knows the display name
        String assignee = bearerToken("it-assignee-sub", "Bob");
        get("/api/projects", assignee);

        assertThat(get("/api/tasks/ZUORD-1", creator).body()).contains("\"assignee_name\":\"Bob\"");
        assertThat(get("/api/tasks?project=ZUORD", creator).body()).contains("\"assignee_name\":\"Bob\"");
    }

    @Test
    void snapshotsTheCommentAuthorName() throws Exception {
        String token = bearerToken("it-author-sub", "Autorin");

        post("/api/projects", """
                {"name":"Kommentare","short_code":"KOMM"}""", token);
        post("/api/comments", """
                {"parent":"project:KOMM","text":"ein Kommentar"}""", token);

        Response comments = get("/api/comments?parent=project:KOMM", token);
        assertThat(comments.body())
                .contains("\"author_sub\":\"it-author-sub\"")
                .contains("\"author_name\":\"Autorin\"")
                .contains("ein Kommentar")
                .contains("\"entity_type\":\"project\"");

        assertThat(get("/api/activities?parent=project:KOMM", token).body())
                .contains("\"event_type\":\"commented\"");
    }
}
