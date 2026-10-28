package de.homelabs.projects.mcp.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Work-package and task status as the adapter sees it: the closed wire set of the API.
 *
 * <p>The constants ARE the wire values (lowercase), so the generated MCP tool schema,
 * the HTTP JSON and the database share one vocabulary — the proven convention from the
 * memory service: the tool-schema generator maps enum CONSTANT NAMES, not {@code @JsonValue},
 * into the schema. {@code @JsonValue} therefore exists only for the outbound serialization
 * of client commands and tool results, and stays redundant by design.
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
        throw new IllegalArgumentException("unknown status '" + value + "'");
    }
}
