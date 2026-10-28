package de.homelabs.projects.domain;

import java.util.List;

/**
 * The requested short code is already in use (409). The body carries alternative
 * codes so an agent retries with one of them instead of guessing interactively.
 */
public class ShortCodeConflictException extends RuntimeException {

    private final transient List<String> suggestions;

    public ShortCodeConflictException(String code, List<String> suggestions) {
        super("short code '" + code + "' is already taken");
        this.suggestions = List.copyOf(suggestions);
    }

    public List<String> suggestions() {
        return suggestions;
    }
}
