package de.homelabs.projects.mcp.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire shape of one task as the REST API returns it: full canonical content, status,
 * optional assignee (with display name), due date, reference, the group it belongs to
 * and its labels.
 */
public record TaskDto(
        UUID id,
        String reference,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("project_short_code") String projectShortCode,
        @JsonProperty("work_package_id") UUID workPackageId,
        @JsonProperty("work_package_reference") String workPackageReference,
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
