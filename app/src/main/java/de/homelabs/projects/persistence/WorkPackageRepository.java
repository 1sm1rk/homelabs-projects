package de.homelabs.projects.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import de.homelabs.projects.domain.ItemStatus;
import de.homelabs.projects.domain.ItemSummary;
import de.homelabs.projects.domain.RichText;
import de.homelabs.projects.domain.WorkPackage;

/**
 * JDBC access to work packages. The reference number comes from the project's
 * shared counter ({@link ProjectRepository#nextSeq}) inside the create transaction;
 * the per-project sequence is unique across work packages AND tasks.
 */
@Repository
public class WorkPackageRepository {

    private static final String COLUMNS = "w.id, w.project_id, p.short_code AS project_code, w.title, "
            + "w.description_html, w.description_text, w.status, w.assignee_sub, w.due_date, w.seq, "
            + "w.created_at, w.updated_at";

    private final JdbcClient jdbc;

    public WorkPackageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID projectId, int seq, String title, String descriptionHtml,
            String descriptionText, ItemStatus status, String assigneeSub, LocalDate dueDate) {
        jdbc.sql("INSERT INTO work_package (id, project_id, seq, title, description_html, description_text, status, "
                        + "assignee_sub, due_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .param(id)
                .param(projectId)
                .param(seq)
                .param(title)
                .param(descriptionHtml)
                .param(descriptionText)
                .param(status.value())
                .param(assigneeSub)
                .param(dueDate)
                .update();
    }

    public Optional<WorkPackage> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM work_package w JOIN project p ON p.id = w.project_id "
                        + "WHERE w.id = ?")
                .param(id)
                .query(this::mapWorkPackage)
                .optional();
    }

    /** Resolves a reference ({@code HP-12}) within the project that owns the code. */
    public Optional<WorkPackage> findByReference(String shortCode, int seq) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM work_package w JOIN project p ON p.id = w.project_id "
                        + "WHERE upper(p.short_code) = upper(?) AND w.seq = ?")
                .param(shortCode)
                .param(seq)
                .query(this::mapWorkPackage)
                .optional();
    }

    public void update(UUID id, String title, String descriptionHtml, String descriptionText, ItemStatus status,
            String assigneeSub, LocalDate dueDate) {
        jdbc.sql("UPDATE work_package SET title = ?, description_html = ?, description_text = ?, status = ?, "
                        + "assignee_sub = ?, due_date = ?, updated_at = now() WHERE id = ?")
                .param(title)
                .param(descriptionHtml)
                .param(descriptionText)
                .param(status.value())
                .param(assigneeSub)
                .param(dueDate)
                .param(id)
                .update();
    }

    /**
     * Lean work-package list. {@code includeInactiveProjects} is the explicit escape
     * hatch: without it, items of deactivated or archived projects stay out of the
     * list.
     */
    public List<ItemSummary> find(String shortCode, List<ItemStatus> statuses, String assigneeSub, String query,
            boolean includeInactiveProjects, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT w.id, p.short_code || '-' || w.seq AS reference, w.title, w.status, w.assignee_sub, "
                        + "w.due_date, w.description_text FROM work_package w "
                        + "JOIN project p ON p.id = w.project_id WHERE TRUE");
        Map<String, Object> params = new LinkedHashMap<>();
        if (!includeInactiveProjects) {
            sql.append(" AND p.status = 'active'");
        }
        if (shortCode != null && !shortCode.isBlank()) {
            sql.append(" AND upper(p.short_code) = upper(:shortCode)");
            params.put("shortCode", shortCode.trim());
        }
        if (statuses != null && !statuses.isEmpty()) {
            sql.append(" AND w.status IN (:statuses)");
            params.put("statuses", statuses.stream().map(ItemStatus::value).toList());
        }
        if (assigneeSub != null && !assigneeSub.isBlank()) {
            sql.append(" AND w.assignee_sub = :assignee");
            params.put("assignee", assigneeSub);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (w.search_text ILIKE :like ESCAPE '\\' "
                    + "OR w.search_tsv @@ plainto_tsquery('german', :query) OR w.search_text %> :query)");
            params.put("like", Search.like(query));
            params.put("query", query);
        }
        sql.append(" ORDER BY w.created_at DESC, w.seq DESC LIMIT :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString())
                .params(params)
                .query((rs, rowNum) -> new ItemSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("reference"),
                        rs.getString("title"),
                        ItemStatus.parse(rs.getString("status")),
                        rs.getString("assignee_sub"),
                        null,
                        rs.getObject("due_date", LocalDate.class),
                        RichText.excerpt(rs.getString("description_text"), Search.EXCERPT_LENGTH)))
                .list();
    }

    private WorkPackage mapWorkPackage(ResultSet rs, int rowNum) throws SQLException {
        int seq = rs.getInt("seq");
        String projectCode = rs.getString("project_code");
        return new WorkPackage(
                rs.getObject("id", UUID.class),
                projectCode + "-" + seq,
                rs.getObject("project_id", UUID.class),
                projectCode,
                rs.getString("title"),
                rs.getString("description_html"),
                rs.getString("description_text"),
                ItemStatus.parse(rs.getString("status")),
                rs.getString("assignee_sub"),
                null,
                rs.getObject("due_date", LocalDate.class),
                seq,
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "updated_at"),
                List.of());
    }
}
