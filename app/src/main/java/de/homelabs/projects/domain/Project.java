package de.homelabs.projects.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A project as returned by the single-resource read: full canonical content,
 * lifecycle status, creator provenance and its labels.
 */
public record Project(
        UUID id,
        String shortCode,
        String name,
        String descriptionHtml,
        String descriptionText,
        ProjectStatus status,
        String ownerSub,
        String ownerName,
        Instant createdAt,
        Instant updatedAt,
        List<Label> labels) {

    public Project {
        labels = List.copyOf(labels);
    }

    public Project withLabels(List<Label> labels) {
        return new Project(id, shortCode, name, descriptionHtml, descriptionText, status, ownerSub, ownerName,
                createdAt, updatedAt, labels);
    }
}
