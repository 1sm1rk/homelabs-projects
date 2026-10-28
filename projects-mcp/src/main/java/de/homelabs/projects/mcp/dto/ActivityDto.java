package de.homelabs.projects.mcp.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire shape of one append-only activity entry. {@code eventType} is one of
 * {@code created}, {@code updated}, {@code status_changed}, {@code labeled},
 * {@code unlabeled}, {@code commented}, {@code attached}, {@code detached};
 * {@code detail} is the compact summary of what changed in that entry.
 *
 * @param source the harness client identity that drove the mutation,
 *               {@code null} when the API was called directly
 */
public record ActivityDto(
        UUID id,
        @JsonProperty("entity_type") EntityType entityType,
        @JsonProperty("entity_id") UUID entityId,
        @JsonProperty("actor_sub") String actorSub,
        @JsonProperty("actor_name") String actorName,
        String source,
        @JsonProperty("event_type") String eventType,
        String detail,
        @JsonProperty("created_at") Instant createdAt) {
}
