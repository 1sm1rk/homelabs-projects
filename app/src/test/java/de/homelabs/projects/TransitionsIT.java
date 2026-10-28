package de.homelabs.projects;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
 * The explicit lifecycle moves and the guard they imply: {@code deactivated} hides a
 * project (but keeps it editable), {@code archived} turns the WHOLE subtree read-only —
 * project, work package, task, comments and attachments all answer 409 — while reads
 * keep working and terminal transitions are refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestSecurityConfig.class)
class TransitionsIT extends ApiTestSupport {

    private static final Path ATTACHMENTS = tempDirectory();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("projects.attachments.root", ATTACHMENTS::toString);
    }

    static Path tempDirectory() {
        try {
            return Files.createTempDirectory("projects-transitions-attachments");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Test
    void deactivateHidesTheProjectButKeepsItEditable() throws Exception {
        String token = bearerToken("it-deactivate-sub");
        post("/api/projects", """
                {"name":"Ruhendes Projekt","short_code":"DEAK"}""", token);

        Response deactivated = post("/api/projects/DEAK/deactivate", "", token);
        assertThat(deactivated.status()).isEqualTo(200);
        assertThat(deactivated.body()).contains("\"status\":\"deactivated\"");

        assertThat(get("/api/projects", token).body()).doesNotContain("\"reference\":\"DEAK\"");
        assertThat(get("/api/projects?status=deactivated", token).body()).contains("\"reference\":\"DEAK\"");

        // still editable while deactivated — only archived is a hard stop
        Response edited = patch("/api/projects/DEAK", """
                {"name":"Ruhendes Projekt (überarbeitet)"}""", token);
        assertThat(edited.status()).isEqualTo(200);

        Response activated = post("/api/projects/DEAK/activate", "", token);
        assertThat(activated.body()).contains("\"status\":\"active\"");
        assertThat(get("/api/projects", token).body()).contains("\"reference\":\"DEAK\"");

        Response activity = get("/api/activities?parent=project:DEAK", token);
        assertThat(activity.body()).contains("active → deactivated").contains("deactivated → active");
    }

    @Test
    void archiveTurnsTheWholeSubtreeReadOnly() throws Exception {
        String token = bearerToken("it-archive-sub");
        post("/api/projects", """
                {"name":"Abgeschlossenes Projekt","short_code":"ARCH"}""", token);
        post("/api/projects/ARCH/work-packages", """
                {"title":"Paket im Archiv"}""", token);
        post("/api/tasks", """
                {"project":"ARCH","work_package_id":"ARCH-1","title":"Aufgabe im Archiv"}""", token);
        post("/api/comments", """
                {"parent":"task:ARCH-2","text":"letzter Kommentar"}""", token);
        Response uploaded = upload("/api/attachments", token, "project:ARCH", "archiv.txt", "text/plain",
                "archiv".getBytes());
        assertThat(uploaded.status()).isEqualTo(201);
        String attachmentId = uuidIn(uploaded.body()).toString();

        Response archived = post("/api/projects/ARCH/archive", "", token);
        assertThat(archived.status()).isEqualTo(200);
        assertThat(archived.body()).contains("\"status\":\"archived\"");

        // every write path into the subtree is a 409
        assertThat(patch("/api/projects/ARCH", """
                {"name":"Versuch"}""", token).status()).isEqualTo(409);
        assertThat(post("/api/projects/ARCH/work-packages", """
                {"title":"Neues Paket"}""", token).status()).isEqualTo(409);
        assertThat(patch("/api/work-packages/ARCH-1", """
                {"title":"Neuer Titel"}""", token).status()).isEqualTo(409);
        assertThat(post("/api/tasks", """
                {"project":"ARCH","title":"Neue Aufgabe"}""", token).status()).isEqualTo(409);
        assertThat(patch("/api/tasks/ARCH-2", """
                {"status":"done"}""", token).status()).isEqualTo(409);
        assertThat(post("/api/comments", """
                {"parent":"task:ARCH-2","text":"zu spät"}""", token).status()).isEqualTo(409);
        assertThat(upload("/api/attachments", token, "task:ARCH-2", "spaet.txt", "text/plain",
                "spaet".getBytes()).status()).isEqualTo(409);
        assertThat(delete("/api/attachments/" + attachmentId, token).status()).isEqualTo(409);
        assertThat(post("/api/projects/ARCH/deactivate", "", token).status()).isEqualTo(409);
        assertThat(post("/api/projects/ARCH/archive", "", token).status()).isEqualTo(409);

        Response refusal = patch("/api/work-packages/ARCH-1", """
                {"title":"Neuer Titel"}""", token);
        assertThat(refusal.body()).contains("archived").contains("read-only");

        // reads stay available
        assertThat(get("/api/projects/ARCH", token).status()).isEqualTo(200);
        assertThat(get("/api/work-packages/ARCH-1", token).status()).isEqualTo(200);
        assertThat(get("/api/tasks/ARCH-2", token).status()).isEqualTo(200);
        assertThat(get("/api/comments?parent=task:ARCH-2", token).body()).contains("letzter Kommentar");
        assertThat(get("/api/projects", token).body()).doesNotContain("\"reference\":\"ARCH\"");
        assertThat(get("/api/projects?status=archived", token).body()).contains("\"reference\":\"ARCH\"");
    }

    @Test
    void refusesRepeatedTransitions() throws Exception {
        String token = bearerToken("it-repeat-sub");
        post("/api/projects", """
                {"name":"Wiederholung","short_code":"REP"}""", token);
        post("/api/projects/REP/deactivate", "", token);

        Response again = post("/api/projects/REP/deactivate", "", token);

        assertThat(again.status()).isEqualTo(409);
        assertThat(again.body()).contains("already deactivated");
    }
}
