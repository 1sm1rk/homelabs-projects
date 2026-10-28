package de.homelabs.projects.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The three levels that carry comments, activity and attachments. Polymorphic in
 * the DB ({@code entity_type} + {@code entity_id}, no foreign key), closed here so
 * an unknown level is rejected at binding time instead of being stored.
 *
 * <p>Constants carry the wire values (lowercase): schema, wire and database share
 * one vocabulary.
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
        throw new IllegalArgumentException(
                "unknown entity type '" + value + "'; allowed values: project, work_package, task");
    }
}
