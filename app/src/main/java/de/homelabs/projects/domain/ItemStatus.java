package de.homelabs.projects.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Work-package and task status. Free moves — the server records them and enforces
 * nothing beyond the archived-project guard; {@code blocked} exists deliberately so
 * agents can query "what is blocked" instead of parsing prose. {@code done} and
 * {@code cancelled} items stay editable (notes, labels, regrouping).
 *
 * <p>Constants carry the wire values (lowercase): schema, wire and database share
 * one vocabulary.
 */
public enum ItemStatus {
    todo("todo"),
    in_progress("in_progress"),
    blocked("blocked"),
    done("done"),
    cancelled("cancelled");

    private final String value;

    ItemStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Parses the wire value, case-insensitive; throws for unknown statuses. */
    @JsonCreator
    public static ItemStatus parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        for (ItemStatus status : values()) {
            if (status.value.equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException(
                "unknown status '" + value + "'; allowed values: todo, in_progress, blocked, done, cancelled");
    }
}
