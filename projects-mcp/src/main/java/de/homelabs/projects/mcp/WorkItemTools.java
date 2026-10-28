package de.homelabs.projects.mcp;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import de.homelabs.projects.mcp.dto.ItemStatus;
import de.homelabs.projects.mcp.dto.ItemSummaryDto;
import de.homelabs.projects.mcp.dto.TaskDto;
import de.homelabs.projects.mcp.dto.WorkPackageDto;
import io.modelcontextprotocol.server.McpSyncServerExchange;

/**
 * Work-item tools: work packages (the planning level that groups tasks) and tasks.
 *
 * <p>Each call is one authenticated HTTP call to the projects REST API. Owner and
 * assignee filters resolve from the forwarded token — the caller is never a tool
 * parameter; {@code source} comes from the MCP initialize handshake.
 */
@Component
public class WorkItemTools {

    private final ProjectsApiClient api;

    public WorkItemTools(ProjectsApiClient api) {
        this.api = api;
    }

    // --- work packages ---------------------------------------------------------

    @McpTool(description = "Create a work package inside an existing project: a slice of work that groups its tasks. "
            + "Use it when the user plans a larger chunk inside a project — a single small step belongs as a task. "
            + "Never store a duplicate of a work package that exists; complete, retitle or reassign it with update_work_package. "
            + "The project is required and decides where the package lives; the server assigns its reference (HP-12) from the project's sequence. "
            + "New work packages start in todo — move them with update_work_package. "
            + "Send description as plain text; pass description_html only for prepared markup, never both. "
            + "assignee_sub is a user's subject; the literal 'me' is only understood by the find filters, so omit it when you do not know the subject.")
    public WorkPackageDto store_work_package(
            McpSyncServerExchange exchange,
            @McpToolParam(description = "Project id, short code or reference the package belongs to", required = true) String project,
            @McpToolParam(description = "Title of the work package", required = true) String title,
            @McpToolParam(description = "Description as plain text; the server wraps it into HTML", required = false) String description,
            @McpToolParam(description = "Description as prepared HTML; never together with description", required = false) String description_html,
            @McpToolParam(description = "Subject of the user to assign; omit for unassigned", required = false) String assignee_sub,
            @McpToolParam(description = "Due date as ISO date (yyyy-mm-dd)", required = false) String due_date,
            @McpToolParam(description = "Label names; a label is created on first assignment", required = false) List<String> labels) {
        return api.createWorkPackage(McpClientIdentity.source(exchange), project, title, description, description_html,
                assignee_sub, due_date, labels);
    }

    @McpTool(description = "Read one work package in full: canonical HTML, derived plain text, status, assignee, due date, reference and labels. "
            + "Use it after a list result — find_work_packages answers with an excerpt only. "
            + "It accepts the UUID or the reference (HP-12); the reference is the precise way to address one item.")
    public WorkPackageDto get_work_package(
            @McpToolParam(description = "UUID or reference (HP-12)", required = true) String id_or_ref) {
        return api.getWorkPackage(id_or_ref);
    }

    @McpTool(description = "Lean list of work packages, newest activity first: reference, title, status, assignee, due date and a ~200-character excerpt. "
            + "Use it to see what a project contains or what is still open. "
            + "status accepts several values in one call, so ask for open work as todo and in_progress together. "
            + "assignee takes a user's subject or the literal 'me' for the calling user. "
            + "Keep limit low — every result line fills your context window, and the full content is one get_work_package away. "
            + "If a query returns nothing, retry with different wording before concluding nothing matches.")
    public List<ItemSummaryDto> find_work_packages(
            @McpToolParam(description = "Project id, short code or reference", required = false) String project,
            @McpToolParam(description = "Statuses to include", required = false) List<ItemStatus> status,
            @McpToolParam(description = "User subject, or 'me' for the calling user", required = false) String assignee,
            @McpToolParam(description = "Text to search for in title and description", required = false) String q,
            @McpToolParam(description = "Maximum number of results (default 20, maximum 100)", required = false) Integer limit) {
        return api.findWorkPackages(project, status, assignee, q, limit);
    }

    @McpTool(description = "Change an existing work package: title, description, status, assignee, due date, labels. "
            + "Use it to complete, retitle, reassign or relabel an item that exists — never store a duplicate. "
            + "Pass only the fields that change; fields you leave out stay as they are. "
            + "An empty string clears assignee_sub or due_date; labels replace the whole set and an empty list removes all of them. "
            + "The project of a work package never changes. "
            + "Writes to a work package of an archived project are rejected. "
            + "The reply is the full record.")
    public WorkPackageDto update_work_package(
            McpSyncServerExchange exchange,
            @McpToolParam(description = "UUID or reference (HP-12)", required = true) String id_or_ref,
            @McpToolParam(description = "New title; omit to keep the current one", required = false) String title,
            @McpToolParam(description = "New description as plain text; omit to keep the current one", required = false) String description,
            @McpToolParam(description = "New description as prepared HTML; never together with description", required = false) String description_html,
            @McpToolParam(description = "New status; omit to keep the current one", required = false) ItemStatus status,
            @McpToolParam(description = "Subject of the user to assign; empty string clears the assignee", required = false) String assignee_sub,
            @McpToolParam(description = "Due date as ISO date (yyyy-mm-dd); empty string clears it", required = false) String due_date,
            @McpToolParam(description = "Replacement label set; omit to keep the current labels", required = false) List<String> labels) {
        return api.updateWorkPackage(McpClientIdentity.source(exchange), id_or_ref, title, description,
                description_html, status, assignee_sub, due_date, labels);
    }

    // --- tasks -----------------------------------------------------------------

    @McpTool(description = "Create a task inside an existing project, optionally grouped into one of its work packages. "
            + "Use it for one concrete step that someone can finish; larger slices belong as work packages that group their tasks. "
            + "Never store a duplicate of a task that exists; complete or change it with update_task. "
            + "The project is required, the work package optional and must belong to the same project. "
            + "New tasks start in todo — move them with update_task. "
            + "Send description as plain text; pass description_html only for prepared markup, never both. "
            + "The server assigns the reference (HP-12) from the project's sequence and returns the full record.")
    public TaskDto store_task(
            McpSyncServerExchange exchange,
            @McpToolParam(description = "Project id, short code or reference the task belongs to", required = true) String project,
            @McpToolParam(description = "Work package id or reference (HP-12) of the same project; omit for an ungrouped task", required = false) String work_package,
            @McpToolParam(description = "Title of the task", required = true) String title,
            @McpToolParam(description = "Description as plain text; the server wraps it into HTML", required = false) String description,
            @McpToolParam(description = "Description as prepared HTML; never together with description", required = false) String description_html,
            @McpToolParam(description = "Subject of the user to assign; omit for unassigned", required = false) String assignee_sub,
            @McpToolParam(description = "Due date as ISO date (yyyy-mm-dd)", required = false) String due_date,
            @McpToolParam(description = "Label names; a label is created on first assignment", required = false) List<String> labels) {
        return api.createTask(McpClientIdentity.source(exchange), project, work_package, title, description,
                description_html, assignee_sub, due_date, labels);
    }

    @McpTool(description = "Read one task in full: canonical HTML, derived plain text, status, assignee, due date, reference, its work package and labels. "
            + "Use it after a list result — find_tasks answers with an excerpt only. "
            + "It accepts the UUID or the reference (HP-12); the reference is the precise way to address one item.")
    public TaskDto get_task(
            @McpToolParam(description = "UUID or reference (HP-12)", required = true) String id_or_ref) {
        return api.getTask(id_or_ref);
    }

    @McpTool(description = "Lean list of tasks, newest activity first: reference, title, status, assignee, due date and a ~200-character excerpt. "
            + "Use it for what is open, what is mine and what is due. "
            + "assignee takes the literal 'me' for the calling user — that is the 'mine' query. "
            + "due_before is an ISO date and includes everything due up to that day; use it when planning a week. "
            + "status accepts several values in one call, so ask for open work as todo and in_progress together. "
            + "Keep limit low — every result line fills your context window, and the full content is one get_task away. "
            + "If a query returns nothing, retry with different wording before concluding nothing matches.")
    public List<ItemSummaryDto> find_tasks(
            @McpToolParam(description = "Project id, short code or reference", required = false) String project,
            @McpToolParam(description = "Work package id or reference (HP-12) to restrict to", required = false) String wp,
            @McpToolParam(description = "Statuses to include", required = false) List<ItemStatus> status,
            @McpToolParam(description = "User subject, or 'me' for the calling user", required = false) String assignee,
            @McpToolParam(description = "ISO date (yyyy-mm-dd); includes tasks due up to that day", required = false) String due_before,
            @McpToolParam(description = "Text to search for in title and description", required = false) String q,
            @McpToolParam(description = "Maximum number of results (default 20, maximum 100)", required = false) Integer limit) {
        return api.findTasks(project, wp, status, assignee, due_before, q, limit);
    }

    @McpTool(description = "Change an existing task: title, description, status, grouping, assignee, due date, labels. "
            + "Use it to complete, retitle, regroup or reassign a task that exists — never store a duplicate. "
            + "Pass only the fields that change; fields you leave out stay as they are. "
            + "An empty string clears work_package, assignee_sub or due_date; labels replace the whole set and an empty list removes all of them. "
            + "A task never changes its project, and a work package must belong to the task's own project. "
            + "Writes to a task of an archived project are rejected. "
            + "The reply is the full record with the work package reference it landed in.")
    public TaskDto update_task(
            McpSyncServerExchange exchange,
            @McpToolParam(description = "UUID or reference (HP-12)", required = true) String id_or_ref,
            @McpToolParam(description = "New title; omit to keep the current one", required = false) String title,
            @McpToolParam(description = "New description as plain text; omit to keep the current one", required = false) String description,
            @McpToolParam(description = "New description as prepared HTML; never together with description", required = false) String description_html,
            @McpToolParam(description = "New status; omit to keep the current one", required = false) ItemStatus status,
            @McpToolParam(description = "Work package id or reference (HP-12) of the same project; empty string removes the grouping", required = false) String work_package,
            @McpToolParam(description = "Subject of the user to assign; empty string clears the assignee", required = false) String assignee_sub,
            @McpToolParam(description = "Due date as ISO date (yyyy-mm-dd); empty string clears it", required = false) String due_date,
            @McpToolParam(description = "Replacement label set; omit to keep the current labels", required = false) List<String> labels) {
        return api.updateTask(McpClientIdentity.source(exchange), id_or_ref, title, description, description_html, status,
                assignee_sub, due_date, work_package, labels);
    }
}
