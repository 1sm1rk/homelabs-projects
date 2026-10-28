package de.homelabs.projects.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import de.homelabs.projects.domain.EntityType;
import de.homelabs.projects.domain.Label;

/**
 * JDBC access to labels and their three join tables.
 *
 * <p>Labels are created on demand at the first assignment (name lowercased, unique
 * case-insensitively) and attached with full-replacement semantics per write: the
 * caller passes the complete set it wants, the repository deletes the previous rows
 * and inserts the new ones.
 */
@Repository
public class LabelRepository {

    private final JdbcClient jdbc;

    public LabelRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Label> findByName(String name) {
        return jdbc.sql("SELECT id, name, color FROM label WHERE lower(name) = lower(?)")
                .param(name)
                .query((rs, rowNum) -> new Label(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("color")))
                .optional();
    }

    public void insert(UUID id, String name) {
        jdbc.sql("INSERT INTO label (id, name) VALUES (?, ?)")
                .param(id)
                .param(name)
                .update();
    }

    public List<Label> findAll() {
        return jdbc.sql("SELECT id, name, color FROM label ORDER BY lower(name)")
                .query((rs, rowNum) -> new Label(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("color")))
                .list();
    }

    public List<Label> forEntity(EntityType type, UUID entityId) {
        return jdbc.sql("SELECT l.id, l.name, l.color FROM " + join(type) + " j "
                        + "JOIN label l ON l.id = j.label_id WHERE j." + column(type) + " = ? ORDER BY lower(l.name)")
                .param(entityId)
                .query((rs, rowNum) -> new Label(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("color")))
                .list();
    }

    /** Batch variant for list endpoints: one query per level instead of one per row. */
    public Map<UUID, List<Label>> forEntities(EntityType type, Collection<UUID> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<Label>> byEntity = new LinkedHashMap<>();
        jdbc.sql("SELECT j." + column(type) + " AS entity_id, l.id, l.name, l.color FROM " + join(type) + " j "
                        + "JOIN label l ON l.id = j.label_id WHERE j." + column(type) + " IN (:ids) "
                        + "ORDER BY lower(l.name)")
                .param("ids", List.copyOf(entityIds))
                .query((rs, rowNum) -> Map.entry(
                        rs.getObject("entity_id", UUID.class),
                        new Label(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("color"))))
                .list()
                .forEach(entry -> byEntity.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(entry.getValue()));
        return byEntity;
    }

    /** Replaces the whole label set of one entity. */
    public void replace(EntityType type, UUID entityId, List<UUID> labelIds) {
        jdbc.sql("DELETE FROM " + join(type) + " WHERE " + column(type) + " = ?")
                .param(entityId)
                .update();
        for (UUID labelId : labelIds) {
            jdbc.sql("INSERT INTO " + join(type) + " (label_id, " + column(type) + ") VALUES (?, ?)")
                    .param(labelId)
                    .param(entityId)
                    .update();
        }
    }

    private static String join(EntityType type) {
        return switch (type) {
            case project -> "label_project";
            case work_package -> "label_work_package";
            case task -> "label_task";
        };
    }

    private static String column(EntityType type) {
        return switch (type) {
            case project -> "project_id";
            case work_package -> "work_package_id";
            case task -> "task_id";
        };
    }
}
