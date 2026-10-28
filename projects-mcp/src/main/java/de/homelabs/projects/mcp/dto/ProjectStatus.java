package de.homelabs.projects.mcp.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Project lifecycle as the adapter sees it: the closed wire set of the API. Status is
 * never a free {@code update_project} field — the three transition tools are the only
 * way it moves, and {@code archived} is a terminal read-only stop.
 *
 * <p>The constants ARE the wire values (lowercase): tool schema, HTTP JSON and database
 * share one vocabulary. {@code @JsonValue} serves the outbound serialization only.
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
        throw new IllegalArgumentException("unknown project status '" + value + "'");
    }
}
