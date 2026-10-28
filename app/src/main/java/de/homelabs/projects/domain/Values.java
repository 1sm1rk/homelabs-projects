package de.homelabs.projects.domain;

import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Small parse helpers shared by the write paths: UUID detection for identifier
 * resolution and the ISO-date parsing of the {@code due_date} field.
 *
 * <p>{@code due_date} travels as text in write payloads because PATCH needs three
 * states: absent (leave unchanged), empty (clear) and a value (parse). Jackson would
 * collapse the first two into {@code null}.
 */
public final class Values {

    /** The value as a UUID, or empty when it is not one (a code or a reference). */
    public static Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    /** Parses an ISO date ({@code yyyy-MM-dd}); empty text means "no date". */
    public static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException(field + " must be an ISO date (yyyy-mm-dd), got '" + value + "'");
        }
    }

    /** Trims to null: blank assignee or blank code input means "not set". */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Values() {
    }
}
