package de.homelabs.projects.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * LEAN work-package or task line for list and search answers (excerpt discipline):
 * reference, title, status, position inside the project, assignee with display name,
 * due date and ~200 characters of the derived plain text. Full content is one
 * {@code GET} away.
 *
 * <p>{@code workPackageId}/{@code workPackageReference} carry the grouping of a TASK
 * row only; work-package rows and ungrouped tasks answer {@code null} on both.
 */
public record ItemSummary(
        UUID id,
        String reference,
        String title,
        ItemStatus status,
        int sortOrder,
        String assigneeSub,
        String assigneeName,
        LocalDate dueDate,
        String excerpt,
        UUID workPackageId,
        String workPackageReference) {

    /** Fills the display name resolved from the local user directory at read time. */
    public ItemSummary withAssigneeName(String name) {
        return new ItemSummary(id, reference, title, status, sortOrder, assigneeSub, name, dueDate, excerpt,
                workPackageId, workPackageReference);
    }
}
