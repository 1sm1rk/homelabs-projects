package de.homelabs.projects.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A short reference of the form {@code CODE-SEQ} ({@code HP-12}): the project's
 * short code plus the per-project sequence number that work packages and tasks
 * share. Reference resolution accepts this form everywhere an id is accepted, so
 * humans and agents never have to carry UUIDs around.
 *
 * <p>Parsing is deliberately strict: an id-shaped string that happens to contain a
 * dash (a UUID) must not be mistaken for a reference, and a bare {@code HP} is a
 * project code, not an item reference.
 */
public record Reference(String code, int seq) {

    private static final Pattern PATTERN = Pattern.compile("^([A-Za-z0-9]{2,6})-(\\d{1,9})$");

    /** Parses {@code CODE-SEQ}; empty when the value is not a reference. */
    public static Optional<Reference> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Reference(matcher.group(1).toUpperCase(Locale.ROOT),
                Integer.parseInt(matcher.group(2))));
    }

    /** The canonical rendering, e.g. {@code HP-12}. */
    public String format() {
        return code + "-" + seq;
    }
}
