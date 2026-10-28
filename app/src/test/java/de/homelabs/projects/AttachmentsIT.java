package de.homelabs.projects;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

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
 * Attachments: one multipart call stores metadata and bytes, the content endpoint
 * serves the stored type plus a SHA-256 {@code ETag}, {@code DELETE} is the one hard
 * delete of the domain — and the size cap answers 413.
 *
 * <p>The volume root is a temporary directory, so the test never touches a real
 * deployment path; the cap is 1 MB here to keep the oversized upload small.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestSecurityConfig.class)
class AttachmentsIT extends ApiTestSupport {

    private static final Path ROOT = tempDirectory();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("projects.attachments.root", ROOT::toString);
        registry.add("projects.attachments.max-size-mb", () -> "1");
    }

    static Path tempDirectory() {
        try {
            return Files.createTempDirectory("projects-attachments");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Test
    void uploadsMetadataAndServesTheBytes() throws Exception {
        String token = bearerToken("it-attach-sub");
        post("/api/projects", """
                {"name":"Anhänge","short_code":"ATT"}""", token);
        byte[] bytes = "hallo attachment".getBytes(StandardCharsets.UTF_8);

        Response uploaded = upload("/api/attachments", token, "project:ATT", "notiz.txt", "text/plain", bytes);

        assertThat(uploaded.status()).isEqualTo(201);
        assertThat(uploaded.body())
                .contains("\"filename\":\"notiz.txt\"")
                .contains("\"mime_type\":\"text/plain\"")
                .contains("\"byte_size\":16")
                .contains("\"entity_type\":\"project\"")
                .contains("\"uploaded_by_name\"");
        assertThat(uploaded.body()).doesNotContain("storage_key");

        UUID id = uuidIn(uploaded.body());
        String expectedHash = sha256(bytes);

        Response metadata = get("/api/attachments/" + id, token);
        assertThat(metadata.status()).isEqualTo(200);
        assertThat(metadata.body()).contains("\"sha256\":\"" + expectedHash + "\"");

        Response list = get("/api/attachments?parent=project:ATT", token);
        assertThat(list.body()).contains(id.toString());

        BinaryResponse content = download("/api/attachments/" + id + "/content", token);
        assertThat(content.status()).isEqualTo(200);
        assertThat(content.contentType()).startsWith("text/plain");
        assertThat(content.etag()).isEqualTo("\"" + expectedHash + "\"");
        assertThat(new String(content.body(), StandardCharsets.UTF_8)).isEqualTo("hallo attachment");

        String storageKey = jdbc.sql("SELECT storage_key FROM attachment WHERE id = ?")
                .param(id)
                .query(String.class)
                .single();
        assertThat(storageKey).doesNotStartWith("/").startsWith("20").contains(id.toString());
        assertThat(Files.readAllBytes(ROOT.resolve(storageKey))).isEqualTo(bytes);

        assertThat(get("/api/activities?parent=project:ATT", token).body())
                .contains("\"event_type\":\"attached\"")
                .contains("notiz.txt");
    }

    @Test
    void deletesMetadataBytesAndRecordsTheDetachment() throws Exception {
        String token = bearerToken("it-attach-delete-sub");
        post("/api/projects", """
                {"name":"Löschung","short_code":"DEL"}""", token);
        Response uploaded = upload("/api/attachments", token, "project:DEL", "weg.txt", "text/plain",
                "verschwindet".getBytes(StandardCharsets.UTF_8));
        UUID id = uuidIn(uploaded.body());
        String storageKey = jdbc.sql("SELECT storage_key FROM attachment WHERE id = ?")
                .param(id)
                .query(String.class)
                .single();

        Response deleted = delete("/api/attachments/" + id, token);

        assertThat(deleted.status()).isEqualTo(200);
        assertThat(deleted.body()).contains("deleted: attachment").contains("weg.txt");
        assertThat(get("/api/attachments/" + id, token).status()).isEqualTo(404);
        assertThat(Files.exists(ROOT.resolve(storageKey))).isFalse();
        assertThat(get("/api/activities?parent=project:DEL", token).body())
                .contains("\"event_type\":\"detached\"")
                .contains("weg.txt");
    }

    @Test
    void rejectsUploadsAboveTheCap() throws Exception {
        String token = bearerToken("it-attach-size-sub");
        post("/api/projects", """
                {"name":"Größengrenze","short_code":"SIZE"}""", token);

        Response tooLarge = upload("/api/attachments", token, "project:SIZE", "gross.bin",
                "application/octet-stream", new byte[2 * 1024 * 1024]);

        assertThat(tooLarge.status()).isEqualTo(413);
        assertThat(tooLarge.body()).contains("max-size-mb");
    }

    @Test
    void requiresATokenAndAKnownParent() throws Exception {
        String token = bearerToken("it-attach-error-sub");
        post("/api/projects", """
                {"name":"Fehler","short_code":"AFEHL"}""", token);

        assertThat(upload("/api/attachments", null, "project:AFEHL", "x.txt", "text/plain",
                "x".getBytes()).status()).isEqualTo(401);

        assertThat(upload("/api/attachments", token, "project:ZZZZ", "x.txt", "text/plain",
                "x".getBytes()).status()).isEqualTo(404);

        assertThat(get("/api/attachments?parent=project:ZZZZ", token).status()).isEqualTo(404);
        assertThat(get("/api/attachments/" + UUID.randomUUID(), token).status()).isEqualTo(404);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
