package de.homelabs.projects.mcp;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import de.homelabs.projects.mcp.dto.ProjectDto;
import de.homelabs.projects.mcp.dto.ProjectStatus;
import de.homelabs.projects.mcp.dto.ProjectSummaryDto;
import io.modelcontextprotocol.server.McpSyncServerExchange;

/**
 * Project tools: register, read, list, edit, and the three EXPLICIT lifecycle moves.
 *
 * <p>Each call is one authenticated HTTP call to the projects REST API. The owner of
 * every mutation is the forwarded token's subject — the API resolves it, so it is never
 * a tool parameter. The {@code source} recorded on a write comes from the MCP initialize
 * handshake, never from a tool argument.
 */
@Component
public class ProjectTools {

    private final ProjectsApiClient api;

    public ProjectTools(ProjectsApiClient api) {
        this.api = api;
    }

    @McpTool(description = "Register a new project: the container work packages, tasks, comments, attachments and labels hang under. "
            + "Use it when the user starts something that spans more than one working session. "
            + "Never store a second project for one that already exists — rename, re-describe or relabel it with update_project. "
            + "Leave short_code out and the server derives one from the name; the code is the project's permanent handle and appears in every reference (HP-12). "
            + "If the code is already taken the error lists free alternatives — retry with one of them instead of inventing another. "
            + "Send description as plain text; pass description_html only when you already hold prepared markup, never both in one call. "
            + "The reply is the full project record: keep its id, the update and transition tools take the id.")
    public ProjectDto store_project(
            McpSyncServerExchange exchange,
            @McpToolParam(description = "Project name", required = true) String name,
            @McpToolParam(description = "Permanent short code used in references like HP-12; omit to derive one from the name", required = false) String short_code,
            @McpToolParam(description = "Description as plain text; the server wraps it into HTML", required = false) String description,
            @McpToolParam(description = "Description as prepared HTML; never together with description", required = false) String description_html,
            @McpToolParam(description = "Label names; a label is created on first assignment", required = false) List<String> labels) {
        return api.createProject(McpClientIdentity.source(exchange), name, short_code, description, description_html,
                labels);
    }

    @McpTool(description = "Read one project in full: canonical HTML, derived plain text, lifecycle status and labels. "
            + "Use it after a list result or before an update — find_projects answers with an excerpt only. "
            + "It accepts the id, the short code (HP) or a reference (HP-12); the short code is the shortest reliable address.")
    public ProjectDto get_project(
            @McpToolParam(description = "Project id, short code (HP) or reference (HP-12)", required = true) String id_or_code) {
        return api.getProject(id_or_code);
    }

    @McpTool(description = "Lean list of projects, newest activity first: reference, name, status, owner, last update and a ~200-character excerpt. "
            + "Use it to find the project to work in. "
            + "Without a status filter only active projects appear; ask for deactivated or archived ones explicitly. "
            + "Keep limit low — every result line fills your context window, and the full description is one get_project away. "
            + "If a query returns nothing, retry with different wording before concluding that no such project exists.")
    public List<ProjectSummaryDto> find_projects(
            @McpToolParam(description = "Project statuses to include; omit for active projects only", required = false) List<ProjectStatus> status,
            @McpToolParam(description = "Restrict to projects carrying this label", required = false) String label,
            @McpToolParam(description = "Text to search for in name and description", required = false) String q,
            @McpToolParam(description = "Maximum number of results (default 20, maximum 100)", required = false) Integer limit) {
        return api.findProjects(status, label, q, limit);
    }

    @McpTool(description = "Change an existing project: name, description, labels. "
            + "Use it for corrections and additions to a project that exists — never store a duplicate. "
            + "Pass only the fields that change; fields you leave out stay as they are, and labels replace the whole set. "
            + "Lifecycle status never moves here — use deactivate_project, activate_project or archive_project. "
            + "Send description as plain text; pass description_html only for prepared markup, never both. "
            + "An archived project is read-only and the API rejects the edit. "
            + "The reply is the full project record.")
    public ProjectDto update_project(
            McpSyncServerExchange exchange,
            @McpToolParam(description = "Project id (UUID) from store_project or find_projects", required = true) String id,
            @McpToolParam(description = "New name; omit to keep the current one", required = false) String name,
            @McpToolParam(description = "New description as plain text; omit to keep the current one", required = false) String description,
            @McpToolParam(description = "New description as prepared HTML; never together with description", required = false) String description_html,
            @McpToolParam(description = "Replacement label set; omit to keep the current labels", required = false) List<String> labels) {
        return api.updateProject(McpClientIdentity.source(exchange), id, name, description, description_html, labels);
    }

    @McpTool(description = "Take a project out of the active view: it disappears from default lists and stays fully editable. "
            + "Use it when work is paused, not finished — this is the reversible removal. "
            + "Activate it again with activate_project when work resumes. "
            + "The reply is the full project record.")
    public ProjectDto deactivate_project(
            McpSyncServerExchange exchange,
            @McpToolParam(description = "Project id (UUID)", required = true) String id) {
        return api.transitionProject(McpClientIdentity.source(exchange), id, "deactivate");
    }

    @McpTool(description = "Bring a deactivated project back into the normal view. "
            + "Use it when work on a paused project resumes. "
            + "An already active project stays active. "
            + "Archived projects are terminal and cannot be reactivated. "
            + "The reply is the full project record.")
    public ProjectDto activate_project(
            McpSyncServerExchange exchange,
            @McpToolParam(description = "Project id (UUID)", required = true) String id) {
        return api.transitionProject(McpClientIdentity.source(exchange), id, "activate");
    }

    @McpTool(description = "Finish a project for good: it and everything under it become read-only. "
            + "Use it only when the project is done and will not change again — every later write, comments and attachments included, is rejected. "
            + "Prefer deactivate_project when the work may resume. "
            + "Ask the user before archiving when the intent is only that nobody is working on it right now. "
            + "The reply is the full project record.")
    public ProjectDto archive_project(
            McpSyncServerExchange exchange,
            @McpToolParam(description = "Project id (UUID)", required = true) String id) {
        return api.transitionProject(McpClientIdentity.source(exchange), id, "archive");
    }
}
