package de.homelabs.projects.mcp.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire shape of one LEAN work-package or task line: reference, title, status, assignee
 * with display name, due date and ~200 characters of derived plain text. Full content
 * is one {@code get_task} / {@code get_work_package} away.
 */
public record ItemSummaryDto(
        UUID id,
        String reference,
        String title,
        ItemStatus status,
        @JsonProperty("assignee_sub") String assigneeSub,
        @JsonProperty("assignee_name") String assigneeName,
        @JsonProperty("due_date") LocalDate dueDate,
        String excerpt) {
}
