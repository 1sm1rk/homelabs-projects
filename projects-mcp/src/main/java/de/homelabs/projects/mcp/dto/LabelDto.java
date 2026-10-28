package de.homelabs.projects.mcp.dto;

import java.util.UUID;

/**
 * Wire shape of one label. Names are unique case-insensitively and stored lowercased;
 * {@code color} is optional and, in this stage, only ever set by an import.
 */
public record LabelDto(UUID id, String name, String color) {
}
