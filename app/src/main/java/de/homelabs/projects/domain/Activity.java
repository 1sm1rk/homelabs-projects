package de.homelabs.projects.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * One append-only activity entry. {@code eventType} is one of {@code created},
 * {@code updated}, {@code status_changed}, {@code labeled}, {@code unlabeled},
 * {@code commented}, {@code attached}, {@code detached}; {@code detail} is the
 * compact summary composed by {@link ActivityDetail}.
 *
 * @param source the harness client identity that drove the mutation, {@code null}
 *               when the API was called directly
 */
public record Activity(
        UUID id,
        EntityType entityType,
        UUID entityId,
        String actorSub,
        String actorName,
        String source,
        String eventType,
        String detail,
        Instant createdAt) {
}
