package de.homelabs.projects.mcp.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The three levels that carry comments, activity and attachments — the closed set the
 * API accepts as {@code parent}. Constants ARE the wire values (lowercase).
 */
public enum EntityType {
    project("project"),
    work_package("work_package"),
    task("task");

    private final String value;

    EntityType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Parses the wire value, case-insensitive; throws for unknown levels. */
    @JsonCreator
    public static EntityType parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        for (EntityType type : values()) {
            if (type.value.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown entity type '" + value + "'");
    }
}
