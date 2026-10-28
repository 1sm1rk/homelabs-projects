package de.homelabs.projects.mcp.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Wire shape of one plain-text comment on a project, work package or task. */
public record CommentDto(
        UUID id,
        @JsonProperty("entity_type") EntityType entityType,
        @JsonProperty("entity_id") UUID entityId,
        @JsonProperty("author_sub") String authorSub,
        @JsonProperty("author_name") String authorName,
        String content,
        @JsonProperty("created_at") Instant createdAt) {
}
