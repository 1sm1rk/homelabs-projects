package de.homelabs.projects.mcp;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import de.homelabs.projects.mcp.dto.ActivityDto;
import de.homelabs.projects.mcp.dto.AttachmentDto;
import de.homelabs.projects.mcp.dto.CommentDto;
import de.homelabs.projects.mcp.dto.EntityType;
import de.homelabs.projects.mcp.dto.LabelDto;
import io.modelcontextprotocol.server.McpSyncServerExchange;

/**
 * Collaboration tools around the three entity levels: comments, the activity log, the
 * label vocabulary and attachment metadata.
 *
 * <p>Comments, activity and attachments hang off a parent given as
 * {@code entity_type} + {@code entity_id}; the client composes the API's
 * {@code {entity_type}:{id-or-reference}} reference out of the two. Attachment BYTES are
 * not part of this surface — the metadata is.
 */
@Component
public class CollaborationTools {

    private final ProjectsApiClient api;

    public CollaborationTools(ProjectsApiClient api) {
        this.api = api;
    }

    @McpTool(description = "Append a plain-text comment to a project, work package or task. "
            + "Use it for decisions, questions and status notes that are not part of the entity's content. "
            + "entity_type names the level and entity_id takes the id or the reference (HP-12); resolve the entity first when the id is unclear, because a comment on the wrong level is invisible to whoever reads that entity. "
            + "Text is stored as plain text; never send markup. "
            + "The reply is the stored comment.")
    public CommentDto store_comment(
            McpSyncServerExchange exchange,
            @McpToolParam(description = "Level the comment belongs to", required = true) EntityType entity_type,
            @McpToolParam(description = "Id or reference of that entity (UUID or HP-12)", required = true) String entity_id,
            @McpToolParam(description = "Comment text, plain text", required = true) String text) {
        return api.createComment(McpClientIdentity.source(exchange), entity_type, entity_id, text);
    }

    @McpTool(description = "The plain-text comments of one entity, newest first. "
            + "Use it before answering what was said about an entity — comments carry the conversation, get_activity carries the mutations. "
            + "entity_type and entity_id address the parent the same way store_comment does. "
            + "Keep limit low — every comment fills your context window.")
    public List<CommentDto> find_comments(
            @McpToolParam(description = "Level the comments belong to", required = true) EntityType entity_type,
            @McpToolParam(description = "Id or reference of that entity (UUID or HP-12)", required = true) String entity_id,
            @McpToolParam(description = "Maximum number of comments (default 50, maximum 200)", required = false) Integer limit) {
        return api.listComments(entity_type, entity_id, limit);
    }

    @McpTool(description = "The append-only activity log of one entity, newest first: created, updated, status changes, labels, comments, attachments. "
            + "Use it to see what changed and who changed it, not what the entity says — get_project, get_work_package or get_task carry the content. "
            + "entity_type and entity_id address the parent the same way store_comment does. "
            + "Keep limit low — every entry fills your context window.")
    public List<ActivityDto> get_activity(
            @McpToolParam(description = "Level of the entity", required = true) EntityType entity_type,
            @McpToolParam(description = "Id or reference of that entity (UUID or HP-12)", required = true) String entity_id,
            @McpToolParam(description = "Maximum number of entries (default 50, maximum 200)", required = false) Integer limit) {
        return api.listActivities(entity_type, entity_id, limit);
    }

    @McpTool(description = "The labels currently in use across projects, work packages and tasks. "
            + "Use it before filtering by label so the filter matches an existing name. "
            + "There is no separate create call: a label is created on first assignment through a store or update tool.")
    public List<LabelDto> list_labels() {
        return api.listLabels();
    }

    @McpTool(description = "Attachment metadata of one entity: file name, type, size, checksum, uploader. "
            + "Metadata only — attachment bytes never travel through these tools in this stage, so a file download stays outside the agent session. "
            + "Use it to report what is attached to an entity. "
            + "entity_type and entity_id address the parent the same way store_comment does.")
    public List<AttachmentDto> list_attachments(
            @McpToolParam(description = "Level of the entity", required = true) EntityType entity_type,
            @McpToolParam(description = "Id or reference of that entity (UUID or HP-12)", required = true) String entity_id) {
        return api.listAttachments(entity_type, entity_id, null);
    }
}
