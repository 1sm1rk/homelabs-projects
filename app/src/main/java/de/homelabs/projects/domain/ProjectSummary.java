package de.homelabs.projects.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * LEAN project line for list and search answers (excerpt discipline): reference,
 * name, status, creator display name, last update and ~200 characters of the
 * derived plain text. Full content is one {@code GET /api/projects/{idOrCode}} away.
 */
public record ProjectSummary(
        UUID id,
        String reference,
        String name,
        ProjectStatus status,
        String ownerName,
        Instant updatedAt,
        String excerpt) {
}
