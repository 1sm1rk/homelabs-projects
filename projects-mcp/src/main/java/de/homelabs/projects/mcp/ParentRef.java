package de.homelabs.projects.mcp;

import de.homelabs.projects.mcp.dto.EntityType;

/**
 * Composes the API's {@code parent} reference out of the two tool parameters an agent
 * sees. The grammar is {@code {entity_type}:{id-or-reference}} ({@code task:HP-12}) —
 * the only unambiguous way to address an entity, because a bare reference resolves
 * tasks before work packages and a bare UUID is probed across all three levels.
 *
 * <p>The tool surface keeps {@code entity_type} and {@code entity_id} separate; only the
 * client collapses them into the one string the API expects.
 */
final class ParentRef {

    /** {@code {entity_type}:{id-or-reference}}; rejects a missing id before any call. */
    static String compose(EntityType entityType, String idOrRef) {
        String value = idOrRef == null ? "" : idOrRef.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("entity_id is required: the id or reference of the entity");
        }
        if (entityType == null) {
            throw new IllegalArgumentException("entity_type is required: project, work_package or task");
        }
        return entityType.value() + ":" + value;
    }

    private ParentRef() {
    }
}
