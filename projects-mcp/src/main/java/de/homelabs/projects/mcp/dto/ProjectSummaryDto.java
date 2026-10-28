package de.homelabs.projects.mcp.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire shape of one LEAN project line (list and search answers): reference, name,
 * status, creator display name, last update and ~200 characters of derived plain text.
 * Full content is one {@code get_project} away.
 */
public record ProjectSummaryDto(
        UUID id,
        String reference,
        String name,
        ProjectStatus status,
        @JsonProperty("owner_name") String ownerName,
        @JsonProperty("updated_at") Instant updatedAt,
        String excerpt) {
}
