package de.homelabs.projects.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A work package as returned by the single-resource read: full canonical content,
 * status, optional assignee (with the display name resolved from the local user
 * directory) and due date, its reference ({@code HP-12}) and its labels.
 */
public record WorkPackage(
        UUID id,
        String reference,
        UUID projectId,
        String projectShortCode,
        String title,
        String descriptionHtml,
        String descriptionText,
        ItemStatus status,
        String assigneeSub,
        String assigneeName,
        LocalDate dueDate,
        int seq,
        Instant createdAt,
        Instant updatedAt,
        List<Label> labels) {

    public WorkPackage {
        labels = List.copyOf(labels);
    }

    public WorkPackage withLabels(List<Label> labels) {
        return new WorkPackage(id, reference, projectId, projectShortCode, title, descriptionHtml,
                descriptionText, status, assigneeSub, assigneeName, dueDate, seq, createdAt, updatedAt, labels);
    }

    /** Fills the display name resolved from the local user directory at read time. */
    public WorkPackage withAssigneeName(String name) {
        return new WorkPackage(id, reference, projectId, projectShortCode, title, descriptionHtml,
                descriptionText, status, assigneeSub, name, dueDate, seq, createdAt, updatedAt, labels);
    }
}
