package de.homelabs.projects.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageKeysTest {

    private static final UUID ID = UUID.fromString("3f2c1b8e-1111-2222-3333-444455556666");

    @Test
    void keysAreRelativeAndDated() {
        assertThat(StorageKeys.key(ID, "plan.pdf", Instant.parse("2026-10-28T12:00:00Z")))
                .isEqualTo("2026/10/" + ID + "/plan.pdf");

        String key = StorageKeys.key(ID, "plan.pdf", Instant.parse("2026-01-02T00:30:00Z"));
        assertThat(key).startsWith("2026/01/").doesNotStartWith("/").doesNotContain("..");
    }

    @Test
    void stripsPathTraversalFromUploadedFilenames() {
        assertThat(StorageKeys.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(StorageKeys.sanitizeFilename("C:\\temp\\bericht.pdf")).isEqualTo("bericht.pdf");
        assertThat(StorageKeys.sanitizeFilename("...hidden")).isEqualTo("hidden");
        assertThat(StorageKeys.sanitizeFilename("a b/ä.txt")).isEqualTo("_.txt");
        assertThat(StorageKeys.sanitizeFilename(null)).isEqualTo("file");
        assertThat(StorageKeys.sanitizeFilename("   ")).isEqualTo("file");
        assertThat(StorageKeys.sanitizeFilename("/")).isEqualTo("file");
    }

    @Test
    void boundsLongFilenamesAndKeepsTheExtension() {
        String sanitized = StorageKeys.sanitizeFilename("x".repeat(300) + ".pptx");

        assertThat(sanitized).hasSizeLessThanOrEqualTo(120).endsWith(".pptx");
    }

    @Test
    void hashesTheBytesForIntegrityAndEtag() {
        assertThat(StorageKeys.sha256Hex("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(StorageKeys.etag("ABC")).isEqualTo("\"abc\"");
        assertThat(StorageKeys.contentType(null)).isEqualTo("application/octet-stream");
        assertThat(StorageKeys.contentType("text/plain")).isEqualTo("text/plain");
    }
}
