package de.homelabs.projects.domain;

/** An upload exceeds the configured attachment size cap (413). */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
