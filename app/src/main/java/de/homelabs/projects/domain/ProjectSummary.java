package de.homelabs.projects.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * LEAN project line for list and search answers (excerpt discipline): reference,
 * name, status, creator display name, last update, the label names attached to the
 * project and ~200 characters of the derived plain text. Full content is one
 * {@code GET /api/projects/{idOrCode}} away.
 */
public record ProjectSummary(
        UUID id,
        String reference,
        String name,
        ProjectStatus status,
        String ownerName,
        Instant updatedAt,
        String excerpt,
        List<String> labels) {

    public ProjectSummary {
        labels = List.copyOf(labels);
    }

    /** Fills the label names read in one batch per list call. */
    public ProjectSummary withLabels(List<String> labels) {
        return new ProjectSummary(id, reference, name, status, ownerName, updatedAt, excerpt, labels);
    }
}
