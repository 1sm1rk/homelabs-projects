package de.homelabs.projects.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import de.homelabs.projects.domain.Activity;
import de.homelabs.projects.domain.EntityType;

/** JDBC access to the append-only activity log: insert and read, never update or delete. */
@Repository
public class ActivityRepository {

    private final JdbcClient jdbc;

    public ActivityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Activity activity) {
        jdbc.sql("INSERT INTO activity (id, entity_type, entity_id, actor_sub, actor_name, source, event_type, "
                        + "detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .param(activity.id())
                .param(activity.entityType().value())
                .param(activity.entityId())
                .param(activity.actorSub())
                .param(activity.actorName())
                .param(activity.source())
                .param(activity.eventType())
                .param(activity.detail())
                .update();
    }

    /** Newest first. */
    public List<Activity> findForEntity(EntityType entityType, UUID entityId, int limit) {
        return jdbc.sql("SELECT id, entity_type, entity_id, actor_sub, actor_name, source, event_type, detail, "
                        + "created_at FROM activity WHERE entity_type = ? AND entity_id = ? "
                        + "ORDER BY created_at DESC, id LIMIT ?")
                .param(entityType.value())
                .param(entityId)
                .param(limit)
                .query((rs, rowNum) -> new Activity(
                        rs.getObject("id", UUID.class),
                        EntityType.parse(rs.getString("entity_type")),
                        rs.getObject("entity_id", UUID.class),
                        rs.getString("actor_sub"),
                        rs.getString("actor_name"),
                        rs.getString("source"),
                        rs.getString("event_type"),
                        rs.getString("detail"),
                        Rows.instant(rs, "created_at")))
                .list();
    }
}
