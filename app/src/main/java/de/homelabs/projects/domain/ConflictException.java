package de.homelabs.projects.domain;

/**
 * The request is well-formed but the current state forbids it (409): an archived
 * project refuses writes, and a project may not repeat or skip a lifecycle
 * transition.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
