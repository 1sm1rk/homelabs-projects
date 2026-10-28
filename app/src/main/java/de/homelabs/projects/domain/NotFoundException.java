package de.homelabs.projects.domain;

/** An entity the caller asked for does not exist (404). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
