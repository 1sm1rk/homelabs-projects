package de.homelabs.projects.domain;

import java.util.UUID;

/**
 * A label as it is attached to a project, work package or task. Names are unique
 * case-insensitively and stored lowercased; {@code color} is optional and, in the
 * first stage, only ever set by a later import (assignment happens by name).
 */
public record Label(UUID id, String name, String color) {
}
