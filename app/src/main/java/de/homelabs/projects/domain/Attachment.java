package de.homelabs.projects.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Attachment metadata. The bytes live on the filesystem volume under the stored
 * storage key — a RELATIVE path (year/month/attachment-id/filename), never an
 * absolute one and never part of the wire contract.
 */
public record Attachment(
        UUID id,
        EntityType entityType,
        UUID entityId,
        String filename,
        String mimeType,
        long byteSize,
        String sha256,
        String uploadedBySub,
        String uploadedByName,
        Instant createdAt) {
}
