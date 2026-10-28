package de.homelabs.projects.domain;

import java.time.Instant;
import java.util.UUID;

/** A plain-text comment on a project, work package or task. */
public record Comment(
        UUID id,
        EntityType entityType,
        UUID entityId,
        String authorSub,
        String authorName,
        String content,
        Instant createdAt) {
}
