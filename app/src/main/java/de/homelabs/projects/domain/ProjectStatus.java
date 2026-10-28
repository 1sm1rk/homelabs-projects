package de.homelabs.projects.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Project lifecycle. Transitions are EXPLICIT REST sub-resources
 * ({@code /deactivate}, {@code /activate}, {@code /archive}) — status is never a
 * free PATCH field. {@code archived} is a hard stop: nothing in the subtree accepts
 * writes any more (409).
 *
 * <ul>
 *   <li>{@code active} → deactivate, archive</li>
 *   <li>{@code deactivated} → activate, archive (the reversible "delete")</li>
 *   <li>{@code archived} → terminal in Stage 1</li>
 * </ul>
 *
 * <p>Constants carry the wire values (lowercase): schema, wire and database share
 * one vocabulary.
 */
public enum ProjectStatus {
    active("active"),
    deactivated("deactivated"),
    archived("archived");

    private final String value;

    ProjectStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Parses the wire value, case-insensitive; throws for unknown statuses. */
    @JsonCreator
    public static ProjectStatus parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        for (ProjectStatus status : values()) {
            if (status.value.equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException(
                "unknown project status '" + value + "'; allowed values: active, deactivated, archived");
    }

    /** Statuses a project may move to from here; {@code archived} has no successors. */
    public List<ProjectStatus> allowedTransitions() {
        return switch (this) {
            case active -> List.of(deactivated, archived);
            case deactivated -> List.of(active, archived);
            case archived -> List.of();
        };
    }

    /** The default list visibility: deactivated and archived projects are hidden. */
    public static List<ProjectStatus> defaultVisible() {
        return List.of(active);
    }
}
