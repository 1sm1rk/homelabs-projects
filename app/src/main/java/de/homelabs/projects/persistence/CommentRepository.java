package de.homelabs.projects.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import de.homelabs.projects.domain.Comment;
import de.homelabs.projects.domain.EntityType;

/** JDBC access to comments (plain text, newest first, create and list only in Stage 1). */
@Repository
public class CommentRepository {

    private final JdbcClient jdbc;

    public CommentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, EntityType entityType, UUID entityId, String authorSub, String authorName,
            String content) {
        jdbc.sql("INSERT INTO comment (id, entity_type, entity_id, author_sub, author_name, content) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")
                .param(id)
                .param(entityType.value())
                .param(entityId)
                .param(authorSub)
                .param(authorName)
                .param(content)
                .update();
    }

    public Optional<Comment> findById(UUID id) {
        return jdbc.sql("SELECT id, entity_type, entity_id, author_sub, author_name, content, created_at "
                        + "FROM comment WHERE id = ?")
                .param(id)
                .query((rs, rowNum) -> new Comment(
                        rs.getObject("id", UUID.class),
                        EntityType.parse(rs.getString("entity_type")),
                        rs.getObject("entity_id", UUID.class),
                        rs.getString("author_sub"),
                        rs.getString("author_name"),
                        rs.getString("content"),
                        Rows.instant(rs, "created_at")))
                .optional();
    }

    /** Newest first, as everything the agents read. */
    public List<Comment> findForEntity(EntityType entityType, UUID entityId, int limit) {
        return jdbc.sql("SELECT id, entity_type, entity_id, author_sub, author_name, content, created_at "
                        + "FROM comment WHERE entity_type = ? AND entity_id = ? "
                        + "ORDER BY created_at DESC, id LIMIT ?")
                .param(entityType.value())
                .param(entityId)
                .param(limit)
                .query((rs, rowNum) -> new Comment(
                        rs.getObject("id", UUID.class),
                        EntityType.parse(rs.getString("entity_type")),
                        rs.getObject("entity_id", UUID.class),
                        rs.getString("author_sub"),
                        rs.getString("author_name"),
                        rs.getString("content"),
                        Rows.instant(rs, "created_at")))
                .list();
    }
}
