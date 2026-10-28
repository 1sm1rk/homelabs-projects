package de.homelabs.projects.mcp.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire shape of one work package as the REST API returns it: full canonical content,
 * status, optional assignee (with display name), due date, its reference
 * ({@code HP-12}) and its labels.
 */
public record WorkPackageDto(
        UUID id,
        String reference,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("project_short_code") String projectShortCode,
        String title,
        @JsonProperty("description_html") String descriptionHtml,
        @JsonProperty("description_text") String descriptionText,
        ItemStatus status,
        @JsonProperty("assignee_sub") String assigneeSub,
        @JsonProperty("assignee_name") String assigneeName,
        @JsonProperty("due_date") LocalDate dueDate,
        int seq,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        List<LabelDto> labels) {
}
