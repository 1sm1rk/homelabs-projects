package de.homelabs.projects.mcp.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire shape of one project as the REST API returns it: full canonical content, derived
 * plain text, lifecycle status and labels.
 *
 * <p>Every component carries its {@code snake_case} wire name explicitly. The API's own
 * Jackson strategy produces snake_case, but the mapper serializing THIS record belongs
 * to whichever JVM composes the adapter (the gateway), and its naming strategy is
 * camelCase. Without the explicit names the API would silently DROP every field whose
 * name it does not know — a mutation would no-op instead of failing.
 */
public record ProjectDto(
        UUID id,
        @JsonProperty("short_code") String shortCode,
        String name,
        @JsonProperty("description_html") String descriptionHtml,
        @JsonProperty("description_text") String descriptionText,
        ProjectStatus status,
        @JsonProperty("owner_sub") String ownerSub,
        @JsonProperty("owner_name") String ownerName,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        List<LabelDto> labels) {
}
