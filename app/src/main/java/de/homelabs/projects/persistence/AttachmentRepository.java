package de.homelabs.projects.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import de.homelabs.projects.domain.Attachment;
import de.homelabs.projects.domain.EntityType;

/**
 * JDBC access to attachment metadata plus the one hard delete of the domain. The
 * bytes are handled by the service (filesystem volume); {@code storage_key} stays
 * internal and never leaves this layer.
 */
@Repository
public class AttachmentRepository {

    private static final String COLUMNS = "id, entity_type, entity_id, filename, mime_type, byte_size, sha256, "
            + "uploaded_by_sub, uploaded_by_name, created_at";

    private final JdbcClient jdbc;

    public AttachmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, EntityType entityType, UUID entityId, String filename, String mimeType, long byteSize,
            String sha256, String storageKey, String uploadedBySub, String uploadedByName) {
        jdbc.sql("INSERT INTO attachment (id, entity_type, entity_id, filename, mime_type, byte_size, sha256, "
                        + "storage_key, uploaded_by_sub, uploaded_by_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .param(id)
                .param(entityType.value())
                .param(entityId)
                .param(filename)
                .param(mimeType)
                .param(byteSize)
                .param(sha256)
                .param(storageKey)
                .param(uploadedBySub)
                .param(uploadedByName)
                .update();
    }

    public Optional<Attachment> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM attachment WHERE id = ?")
                .param(id)
                .query(this::mapAttachment)
                .optional();
    }

    public Optional<String> findStorageKey(UUID id) {
        return jdbc.sql("SELECT storage_key FROM attachment WHERE id = ?")
                .param(id)
                .query(String.class)
                .optional();
    }

    /** Metadata only, newest first. */
    public List<Attachment> findForEntity(EntityType entityType, UUID entityId, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM attachment WHERE entity_type = ? AND entity_id = ? "
                        + "ORDER BY created_at DESC, id LIMIT ?")
                .param(entityType.value())
                .param(entityId)
                .param(limit)
                .query(this::mapAttachment)
                .list();
    }

    public void delete(UUID id) {
        jdbc.sql("DELETE FROM attachment WHERE id = ?")
                .param(id)
                .update();
    }

    private Attachment mapAttachment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Attachment(
                rs.getObject("id", UUID.class),
                EntityType.parse(rs.getString("entity_type")),
                rs.getObject("entity_id", UUID.class),
                rs.getString("filename"),
                rs.getString("mime_type"),
                rs.getLong("byte_size"),
                rs.getString("sha256"),
                rs.getString("uploaded_by_sub"),
                rs.getString("uploaded_by_name"),
                Rows.instant(rs, "created_at"));
    }
}
