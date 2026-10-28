package de.homelabs.projects.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * LEAN work-package or task line for list and search answers (excerpt discipline):
 * reference, title, status, assignee with display name, due date and ~200 characters
 * of the derived plain text. Full content is one {@code GET} away.
 */
public record ItemSummary(
        UUID id,
        String reference,
        String title,
        ItemStatus status,
        String assigneeSub,
        String assigneeName,
        LocalDate dueDate,
        String excerpt) {

    /** Fills the display name resolved from the local user directory at read time. */
    public ItemSummary withAssigneeName(String name) {
        return new ItemSummary(id, reference, title, status, assigneeSub, name, dueDate, excerpt);
    }
}
