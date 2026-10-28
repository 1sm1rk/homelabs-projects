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
import de.homelabs.projects.domain.Task;

/**
 * JDBC access to tasks. The reference number comes from the project's shared counter
 * ({@link ProjectRepository#nextSeq}) inside the create transaction, so task
 * references never collide with work-package references of the same project.
 *
 * <p>The work-package link is optional and regroupable; the composite foreign key
 * {@code (work_package_id, project_id)} keeps a task inside its own project.
 */
@Repository
public class TaskRepository {

    private static final String COLUMNS = "t.id, t.project_id, p.short_code AS project_code, t.work_package_id, "
            + "wp.seq AS work_package_seq, t.title, t.description_html, t.description_text, t.status, "
            + "t.assignee_sub, t.due_date, t.seq, t.created_at, t.updated_at";

    private static final String FROM = " FROM task t JOIN project p ON p.id = t.project_id "
            + "LEFT JOIN work_package wp ON wp.id = t.work_package_id ";

    private final JdbcClient jdbc;

    public TaskRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID projectId, UUID workPackageId, int seq, String title, String descriptionHtml,
            String descriptionText, ItemStatus status, String assigneeSub, LocalDate dueDate) {
        jdbc.sql("INSERT INTO task (id, project_id, work_package_id, seq, title, description_html, description_text, "
                        + "status, assignee_sub, due_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .param(id)
                .param(projectId)
                .param(workPackageId)
                .param(seq)
                .param(title)
                .param(descriptionHtml)
                .param(descriptionText)
                .param(status.value())
                .param(assigneeSub)
                .param(dueDate)
                .update();
    }

    public Optional<Task> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + FROM + "WHERE t.id = ?")
                .param(id)
                .query(this::mapTask)
                .optional();
    }

    /** Resolves a reference ({@code HP-12}) within the project that owns the code. */
    public Optional<Task> findByReference(String shortCode, int seq) {
        return jdbc.sql("SELECT " + COLUMNS + FROM + "WHERE upper(p.short_code) = upper(?) AND t.seq = ?")
                .param(shortCode)
                .param(seq)
                .query(this::mapTask)
                .optional();
    }

    public void update(UUID id, UUID workPackageId, String title, String descriptionHtml, String descriptionText,
            ItemStatus status, String assigneeSub, LocalDate dueDate) {
        jdbc.sql("UPDATE task SET work_package_id = ?, title = ?, description_html = ?, description_text = ?, "
                        + "status = ?, assignee_sub = ?, due_date = ?, updated_at = now() WHERE id = ?")
                .param(workPackageId)
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
     * Lean task list. {@code includeInactiveProjects} is the explicit escape hatch:
     * without it, tasks of deactivated or archived projects stay out of the list.
     */
    public List<ItemSummary> find(String projectCode, UUID workPackageId, List<ItemStatus> statuses,
            String assigneeSub, LocalDate dueBefore, String query, boolean includeInactiveProjects, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT t.id, p.short_code || '-' || t.seq AS reference, t.title, t.status, t.assignee_sub, "
                        + "t.due_date, t.description_text FROM task t "
                        + "JOIN project p ON p.id = t.project_id WHERE TRUE");
        Map<String, Object> params = new LinkedHashMap<>();
        if (!includeInactiveProjects) {
            sql.append(" AND p.status = 'active'");
        }
        if (projectCode != null && !projectCode.isBlank()) {
            sql.append(" AND upper(p.short_code) = upper(:projectCode)");
            params.put("projectCode", projectCode.trim());
        }
        if (workPackageId != null) {
            sql.append(" AND t.work_package_id = :workPackageId");
            params.put("workPackageId", workPackageId);
        }
        if (statuses != null && !statuses.isEmpty()) {
            sql.append(" AND t.status IN (:statuses)");
            params.put("statuses", statuses.stream().map(ItemStatus::value).toList());
        }
        if (assigneeSub != null && !assigneeSub.isBlank()) {
            sql.append(" AND t.assignee_sub = :assignee");
            params.put("assignee", assigneeSub);
        }
        if (dueBefore != null) {
            sql.append(" AND t.due_date IS NOT NULL AND t.due_date <= :dueBefore");
            params.put("dueBefore", dueBefore);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (t.search_text ILIKE :like ESCAPE '\\' "
                    + "OR t.search_tsv @@ plainto_tsquery('german', :query) OR t.search_text %> :query)");
            params.put("like", Search.like(query));
            params.put("query", query);
        }
        sql.append(" ORDER BY t.created_at DESC, t.seq DESC LIMIT :limit");
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

    private Task mapTask(ResultSet rs, int rowNum) throws SQLException {
        int seq = rs.getInt("seq");
        String projectCode = rs.getString("project_code");
        UUID workPackageId = rs.getObject("work_package_id", UUID.class);
        Integer workPackageSeq = workPackageId == null ? null : rs.getInt("work_package_seq");
        return new Task(
                rs.getObject("id", UUID.class),
                projectCode + "-" + seq,
                rs.getObject("project_id", UUID.class),
                projectCode,
                workPackageId,
                workPackageSeq == null ? null : projectCode + "-" + workPackageSeq,
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
