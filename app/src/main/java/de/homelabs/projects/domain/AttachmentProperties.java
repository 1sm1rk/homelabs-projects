package de.homelabs.projects.domain;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Attachment storage configuration: the volume root the bytes live under and the
 * upload cap. The root is deployment data (like the embedding model path of the
 * memory service) — never a working-directory-relative path, always configurable.
 *
 * @param root      directory that holds the attachment tree; storage keys in the DB
 *                  are relative to it
 * @param maxSizeMb upload cap in megabytes, {@code 0} meaning unlimited
 */
@ConfigurationProperties(prefix = "projects.attachments")
public record AttachmentProperties(String root, @DefaultValue("20") int maxSizeMb) {

    public AttachmentProperties {
        if (root == null || root.isBlank()) {
            throw new IllegalStateException(
                    "projects.attachments.root must be configured (env PROJECTS_ATTACHMENT_ROOT)");
        }
    }

    /** The volume root as a path, created on first use. */
    public Path rootPath() {
        return Path.of(root);
    }

    /** The cap in bytes; {@link Long#MAX_VALUE} when unlimited. */
    public long maxSizeBytes() {
        return maxSizeMb <= 0 ? Long.MAX_VALUE : maxSizeMb * 1024L * 1024L;
    }
}
