package de.homelabs.projects.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Attachment storage keys and integrity data.
 *
 * <p>The key is RELATIVE to the configured volume root —
 * {@code yyyy/MM/{attachment-id}/{sanitized-filename}} — so the database never
 * carries a machine-specific absolute path and the volume can be relocated, backed
 * up or rsync'd without touching rows. The filename is sanitized down to a safe
 * character set (no separators, no leading dots, bounded length) so a hostile upload
 * name cannot escape the tree.
 */
public final class StorageKeys {

    private static final int MAX_FILENAME = 120;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM")
            .withZone(ZoneOffset.UTC);

    /** The storage key of an attachment, relative to the volume root. */
    public static String key(UUID id, String safeFilename, Instant now) {
        return MONTH.format(now) + "/" + id + "/" + safeFilename;
    }

    /** Reduces an uploaded filename to {@code [A-Za-z0-9._-]}, bounded, never empty. */
    public static String sanitizeFilename(String filename) {
        String base = filename == null ? "" : filename;
        int separator = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (separator >= 0) {
            base = base.substring(separator + 1);
        }
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^\\.+", "");
        if (cleaned.length() > MAX_FILENAME) {
            int dot = cleaned.lastIndexOf('.');
            String extension = (dot > 0 && cleaned.length() - dot <= 16) ? cleaned.substring(dot) : "";
            cleaned = cleaned.substring(0, MAX_FILENAME - extension.length()) + extension;
        }
        // A name of separators, spaces and dots only carries no information at all.
        return cleaned.replaceAll("[._-]", "").isBlank() ? "file" : cleaned;
    }

    /** Lower-case hex SHA-256 of the stored bytes (the content ETag and integrity check). */
    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /** The ETag form of a digest (quoted, as HTTP expects it). */
    public static String etag(String sha256Hex) {
        return '"' + sha256Hex.toLowerCase(Locale.ROOT) + '"';
    }

    /** Placeholder for {@code application/octet-stream} when the client sends no type. */
    public static String contentType(String requested) {
        return (requested == null || requested.isBlank()) ? "application/octet-stream" : requested;
    }

    /** UTF-8 bytes of a text body (used by tests and small text imports). */
    public static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private StorageKeys() {
    }
}
