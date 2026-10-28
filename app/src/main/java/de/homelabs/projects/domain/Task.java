package de.homelabs.projects.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A task as returned by the single-resource read. It always belongs to a project
 * and may be (re)grouped into one of that project's work packages — the reference
 * of the group travels along so an agent needs no second lookup.
 */
public record Task(
        UUID id,
        String reference,
        UUID projectId,
        String projectShortCode,
        UUID workPackageId,
        String workPackageReference,
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

    public Task {
        labels = List.copyOf(labels);
    }

    public Task withLabels(List<Label> labels) {
        return new Task(id, reference, projectId, projectShortCode, workPackageId, workPackageReference, title,
                descriptionHtml, descriptionText, status, assigneeSub, assigneeName, dueDate, seq, createdAt,
                updatedAt, labels);
    }

    /** Fills the display name resolved from the local user directory at read time. */
    public Task withAssigneeName(String name) {
        return new Task(id, reference, projectId, projectShortCode, workPackageId, workPackageReference, title,
                descriptionHtml, descriptionText, status, assigneeSub, name, dueDate, seq, createdAt, updatedAt,
                labels);
    }
}
