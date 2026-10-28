package de.homelabs.projects.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import de.homelabs.projects.domain.Project;
import de.homelabs.projects.domain.ProjectStatus;
import de.homelabs.projects.domain.ProjectSummary;
import de.homelabs.projects.domain.RichText;

/**
 * JDBC access to projects.
 *
 * <p>{@link #nextSeq} is the single per-project counter shared by work packages and
 * tasks: both take their reference number ({@code HP-12}) from it inside their create
 * transaction, so references are unique per project ACROSS the two item tables. That
 * cross-table uniqueness is a service-layer invariant; the per-table
 * {@code UNIQUE (project_id, seq)} indexes protect each table on its own.
 *
 * <p>Labels are not part of the row mapping — the label tables are read and written by
 * {@link LabelRepository}, and the service composes the full record.
 */
@Repository
public class ProjectRepository {

    private static final String COLUMNS = "p.id, p.short_code, p.name, p.description_html, p.description_text, "
            + "p.status, p.owner_sub, p.owner_name, p.created_at, p.updated_at";

    private final JdbcClient jdbc;

    public ProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, String shortCode, String name, String descriptionHtml, String descriptionText,
            String ownerSub, String ownerName) {
        jdbc.sql("INSERT INTO project (id, short_code, name, description_html, description_text, owner_sub, owner_name) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")
                .param(id)
                .param(shortCode)
                .param(name)
                .param(descriptionHtml)
                .param(descriptionText)
                .param(ownerSub)
                .param(ownerName)
                .update();
    }

    public Optional<Project> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM project p WHERE p.id = ?")
                .param(id)
                .query(this::mapProject)
                .optional();
    }

    public Optional<Project> findByShortCode(String shortCode) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM project p WHERE upper(p.short_code) = upper(?)")
                .param(shortCode)
                .query(this::mapProject)
                .optional();
    }

    public boolean existsByShortCode(String shortCode) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM project p WHERE upper(p.short_code) = upper(?))")
                .param(shortCode)
                .query(Boolean.class)
                .single();
    }

    /**
     * Takes the next reference number of a project: one counter shared by work
     * packages and tasks (cross-table uniqueness), incremented in the create
     * transaction of the item.
     */
    public int nextSeq(UUID projectId) {
        return jdbc.sql("UPDATE project SET next_seq = next_seq + 1 WHERE id = ? RETURNING next_seq")
                .param(projectId)
                .query(Integer.class)
                .single();
    }

    public void updateContent(UUID id, String name, String descriptionHtml, String descriptionText) {
        jdbc.sql("UPDATE project SET name = ?, description_html = ?, description_text = ?, updated_at = now() "
                        + "WHERE id = ?")
                .param(name)
                .param(descriptionHtml)
                .param(descriptionText)
                .param(id)
                .update();
    }

    public void updateStatus(UUID id, ProjectStatus status) {
        jdbc.sql("UPDATE project SET status = ?, updated_at = now() WHERE id = ?")
                .param(status.value())
                .param(id)
                .update();
    }

    /**
     * Lean project list. {@code statuses} is never empty — without a filter the caller
     * passes {@link ProjectStatus#defaultVisible()}, so deactivated and archived
     * projects stay out of the default view. The label filter matches on the label
     * name, case-insensitively; the free-text filter uses the three lexical arms.
     */
    public List<ProjectSummary> find(List<ProjectStatus> statuses, String label, String query, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.short_code, p.name, p.status, p.owner_name, p.updated_at, p.description_text "
                        + "FROM project p WHERE p.status IN (:statuses)");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("statuses", statuses.stream().map(ProjectStatus::value).toList());
        if (label != null && !label.isBlank()) {
            sql.append(" AND EXISTS (SELECT 1 FROM label_project lp JOIN label l ON l.id = lp.label_id "
                    + "WHERE lp.project_id = p.id AND lower(l.name) = lower(:label))");
            params.put("label", label.trim());
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (p.search_text ILIKE :like ESCAPE '\\' "
                    + "OR p.search_tsv @@ plainto_tsquery('german', :query) OR p.search_text %> :query)");
            params.put("like", Search.like(query));
            params.put("query", query);
        }
        sql.append(" ORDER BY p.updated_at DESC, p.id LIMIT :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString())
                .params(params)
                .query((rs, rowNum) -> new ProjectSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("short_code"),
                        rs.getString("name"),
                        ProjectStatus.parse(rs.getString("status")),
                        rs.getString("owner_name"),
                        Rows.instant(rs, "updated_at"),
                        RichText.excerpt(rs.getString("description_text"), Search.EXCERPT_LENGTH)))
                .list();
    }

    private Project mapProject(ResultSet rs, int rowNum) throws SQLException {
        return new Project(
                rs.getObject("id", UUID.class),
                rs.getString("short_code"),
                rs.getString("name"),
                rs.getString("description_html"),
                rs.getString("description_text"),
                ProjectStatus.parse(rs.getString("status")),
                rs.getString("owner_sub"),
                rs.getString("owner_name"),
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "updated_at"),
                List.of());
    }
}
