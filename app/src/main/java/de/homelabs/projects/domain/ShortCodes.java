package de.homelabs.projects.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Short-code (Kurzschrift) rules: {@code ^[A-Z0-9]{2,6}$}, unique
 * case-insensitively, immutable after creation — references ({@code HP-12}) must
 * never change meaning.
 *
 * <p>The code is optional on create: when absent the server derives one from the
 * name (uppercase, strip everything outside {@code [A-Z0-9]}, first six
 * characters). A derived code shorter than two characters cannot be unique enough
 * and is rejected with an explicit request for a code. A collision is answered with
 * {@code 409} plus three alternative suggestions, so an agent retries instead of
 * guessing interactively.
 */
public final class ShortCodes {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9]{2,6}$");
    private static final int MAX_LENGTH = 6;

    /** Normalizes and validates a caller-supplied code; throws with the rule on violation. */
    public static String normalize(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!VALID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "short_code must match " + VALID.pattern() + " (2-6 characters, A-Z and 0-9)");
        }
        return normalized;
    }

    /**
     * Derives a candidate from a project name: uppercase, drop everything outside
     * {@code [A-Z0-9]}, keep the first six characters. May be too short — the caller
     * decides whether that is an error.
     */
    public static String derive(String name) {
        String derived = name == null ? "" : name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return derived.length() > MAX_LENGTH ? derived.substring(0, MAX_LENGTH) : derived;
    }

    /**
     * The collision answer: the requested code suffixed {@code 2..9}, first three
     * that are not taken. A six-character code is shortened by one character first so
     * the suggestion stays within the format.
     */
    public static List<String> suggestions(String code, Predicate<String> taken) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        String prefix = normalized.length() >= MAX_LENGTH ? normalized.substring(0, MAX_LENGTH - 1) : normalized;
        List<String> suggestions = new ArrayList<>(3);
        for (int digit = 2; digit <= 9 && suggestions.size() < 3; digit++) {
            String candidate = prefix + digit;
            if (VALID.matcher(candidate).matches() && !taken.test(candidate)) {
                suggestions.add(candidate);
            }
        }
        return List.copyOf(suggestions);
    }

    private ShortCodes() {
    }
}
