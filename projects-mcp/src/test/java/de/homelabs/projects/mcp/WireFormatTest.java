package de.homelabs.projects.mcp;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.homelabs.projects.mcp.dto.ActivityDto;
import de.homelabs.projects.mcp.dto.AttachmentDto;
import de.homelabs.projects.mcp.dto.CommentDto;
import de.homelabs.projects.mcp.dto.EntityType;
import de.homelabs.projects.mcp.dto.ItemStatus;
import de.homelabs.projects.mcp.dto.ItemSummaryDto;
import de.homelabs.projects.mcp.dto.LabelDto;
import de.homelabs.projects.mcp.dto.ProjectDto;
import de.homelabs.projects.mcp.dto.ProjectStatus;
import de.homelabs.projects.mcp.dto.ProjectSummaryDto;
import de.homelabs.projects.mcp.dto.TaskDto;
import de.homelabs.projects.mcp.dto.WorkPackageDto;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wire vocabulary of the adapter, tested against a mapper with a DEFAULT (camelCase)
 * naming strategy — the worst case, because that is what the composing JVM may provide.
 *
 * <p>Every command must serialize to the API's snake_case names anyway, and every DTO
 * must round-trip from a snake_case body. The field-name assertions are the guard against
 * the silent-drop class: the API ignores unknown properties, so a camelCase name would
 * vanish without an error and a mutation would change nothing.
 */
class WireFormatTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void storeProjectCommandCarriesTheSnakeCaseNames() {
        String json = write(new ProjectsApiClient.StoreProjectCommand("Homelabs Platform", "HP", "plain",
                "<p>html</p>", List.of("infra")));

        assertThat(json).isEqualTo("{\"name\":\"Homelabs Platform\",\"short_code\":\"HP\",\"description\":\"plain\","
                + "\"description_html\":\"<p>html</p>\",\"labels\":[\"infra\"]}");
    }

    @Test
    void updateProjectCommandKeepsUnsetFieldsAsNull() {
        String json = write(new ProjectsApiClient.UpdateProjectCommand("Renamed", null, null, List.of()));

        assertThat(json).isEqualTo("{\"name\":\"Renamed\",\"description\":null,\"description_html\":null,"
                + "\"labels\":[]}");
    }

    @Test
    void workPackageCommandsCarryTheSnakeCaseNames() {
        assertThat(keys(write(new ProjectsApiClient.StoreWorkPackageCommand("t", "d", null, "user-1",
                "2026-11-15", null))))
                .containsOnlyKeys("title", "description", "description_html", "assignee_sub", "due_date", "labels");

        assertThat(keys(write(new ProjectsApiClient.UpdateWorkPackageCommand("t", null, null, ItemStatus.blocked,
                "", "", List.of()))))
                .containsOnlyKeys("title", "description", "description_html", "status", "assignee_sub", "due_date",
                        "labels");
    }

    @Test
    void taskCommandsCarryTheSnakeCaseNames() {
        assertThat(keys(write(new ProjectsApiClient.StoreTaskCommand("t", null, null, "HP", "HP-12", "user-1",
                "2026-11-01", List.of()))))
                .containsOnlyKeys("title", "description", "description_html", "project", "work_package_id",
                        "assignee_sub", "due_date", "labels");

        assertThat(keys(write(new ProjectsApiClient.UpdateTaskCommand(null, null, null, null, null, null, "",
                null))))
                .containsOnlyKeys("title", "description", "description_html", "status", "assignee_sub", "due_date",
                        "work_package_id", "labels");
    }

    @Test
    void commentCommandSendsParentAndText() {
        assertThat(write(new ProjectsApiClient.StoreCommentCommand("task:HP-12", "note")))
                .isEqualTo("{\"parent\":\"task:HP-12\",\"text\":\"note\"}");
    }

    @Test
    void projectDtoRoundTripsFromTheApiBody() {
        ProjectDto project = mapper.readValue(WireFixtures.PROJECT, ProjectDto.class);

        assertThat(project.shortCode()).isEqualTo("HP");
        assertThat(project.descriptionHtml()).isEqualTo("<p>Infrastructure work</p>");
        assertThat(project.descriptionText()).isEqualTo("Infrastructure work");
        assertThat(project.status()).isEqualTo(ProjectStatus.active);
        assertThat(project.ownerSub()).isEqualTo("user-1");
        assertThat(project.ownerName()).isEqualTo("owner");
        assertThat(project.createdAt()).isEqualTo(java.time.Instant.parse("2026-10-28T09:00:00Z"));
        assertThat(project.updatedAt()).isEqualTo(java.time.Instant.parse("2026-10-28T09:30:00Z"));
        assertThat(project.labels()).singleElement().satisfies(label -> assertThat(label.name()).isEqualTo("infra"));
    }

    @Test
    void workPackageDtoAndTaskDtoRoundTripFromTheApiBodies() {
        WorkPackageDto workPackage = mapper.readValue(WireFixtures.WORK_PACKAGE, WorkPackageDto.class);
        TaskDto task = mapper.readValue(WireFixtures.TASK, TaskDto.class);

        assertThat(workPackage.projectId()).isEqualTo(task.projectId());
        assertThat(workPackage.projectShortCode()).isEqualTo("HP");
        assertThat(workPackage.descriptionHtml()).isEqualTo("<p>twenty tools</p>");
        assertThat(workPackage.dueDate()).isEqualTo(java.time.LocalDate.parse("2026-11-15"));
        assertThat(workPackage.seq()).isEqualTo(12);
        assertThat(task.workPackageId()).isEqualTo(workPackage.id());
        assertThat(task.workPackageReference()).isEqualTo("HP-12");
        assertThat(task.assigneeSub()).isEqualTo("user-1");
        assertThat(task.assigneeName()).isEqualTo("owner");
        assertThat(task.seq()).isEqualTo(13);
    }

    @Test
    void leanDtosRoundTripFromTheApiBodies() {
        ProjectSummaryDto project = mapper.readValue("""
                {"id":"11111111-1111-1111-1111-111111111111","reference":"HP","name":"Homelabs Platform",
                 "status":"deactivated","owner_name":"owner","updated_at":"2026-10-28T09:30:00Z","excerpt":"ex"}""",
                ProjectSummaryDto.class);
        ItemSummaryDto item = mapper.readValue("""
                {"id":"44444444-4444-4444-4444-444444444444","reference":"HP-13","title":"Write the client",
                 "status":"in_progress","assignee_sub":"user-1","assignee_name":"owner",
                 "due_date":"2026-11-01","excerpt":"body"}""", ItemSummaryDto.class);

        assertThat(project.status()).isEqualTo(ProjectStatus.deactivated);
        assertThat(project.ownerName()).isEqualTo("owner");
        assertThat(item.assigneeName()).isEqualTo("owner");
        assertThat(item.dueDate()).isEqualTo(java.time.LocalDate.parse("2026-11-01"));
    }

    @Test
    void collaborationDtosRoundTripFromTheApiBodies() {
        CommentDto comment = mapper.readValue("""
                {"id":"55555555-5555-5555-5555-555555555555","entity_type":"work_package",
                 "entity_id":"33333333-3333-3333-3333-333333333333","author_sub":"user-1",
                 "author_name":"owner","content":"looks good","created_at":"2026-10-28T10:00:00Z"}""",
                CommentDto.class);
        ActivityDto activity = mapper.readValue("""
                {"id":"66666666-6666-6666-6666-666666666666","entity_type":"task",
                 "entity_id":"44444444-4444-4444-4444-444444444444","actor_sub":"user-1","actor_name":"owner",
                 "source":"opencode","event_type":"status_changed","detail":"todo -> done",
                 "created_at":"2026-10-28T10:00:00Z"}""", ActivityDto.class);
        AttachmentDto attachment = mapper.readValue("""
                {"id":"77777777-7777-7777-7777-777777777777","entity_type":"project",
                 "entity_id":"11111111-1111-1111-1111-111111111111","filename":"plan.pdf",
                 "mime_type":"application/pdf","byte_size":2048,"sha256":"abc","uploaded_by_sub":"user-1",
                 "uploaded_by_name":"owner","created_at":"2026-10-28T10:00:00Z"}""", AttachmentDto.class);

        assertThat(comment.entityType()).isEqualTo(EntityType.work_package);
        assertThat(comment.authorSub()).isEqualTo("user-1");
        assertThat(activity.eventType()).isEqualTo("status_changed");
        assertThat(activity.actorName()).isEqualTo("owner");
        assertThat(attachment.mimeType()).isEqualTo("application/pdf");
        assertThat(attachment.byteSize()).isEqualTo(2048);
        assertThat(attachment.uploadedByName()).isEqualTo("owner");
    }

    @Test
    void dtosSerializeBackWithSnakeCaseNames() {
        ProjectDto project = mapper.readValue(WireFixtures.PROJECT, ProjectDto.class);
        AttachmentDto attachment = mapper.readValue("""
                {"id":"77777777-7777-7777-7777-777777777777","entity_type":"project",
                 "entity_id":"11111111-1111-1111-1111-111111111111","filename":"plan.pdf",
                 "mime_type":"application/pdf","byte_size":2048,"sha256":"abc","uploaded_by_sub":"user-1",
                 "uploaded_by_name":"owner","created_at":"2026-10-28T10:00:00Z"}""", AttachmentDto.class);
        LabelDto label = new LabelDto(java.util.UUID.randomUUID(), "infra", null);

        assertThat(keys(write(project))).containsKeys("short_code", "description_html", "description_text", "owner_sub",
                "owner_name", "created_at", "updated_at");
        assertThat(keys(write(attachment))).containsKeys("entity_type", "entity_id", "mime_type", "byte_size",
                "uploaded_by_sub", "uploaded_by_name", "created_at");
        assertThat(write(label)).isEqualTo("{\"id\":\"" + label.id() + "\",\"name\":\"infra\",\"color\":null}");
    }

    @Test
    void statusVocabulariesUseTheWireValuesAsConstantNames() {
        assertThat(List.of(ItemStatus.values())).extracting(Enum::name)
                .containsExactly("todo", "in_progress", "blocked", "done", "cancelled");
        assertThat(List.of(ProjectStatus.values())).extracting(Enum::name)
                .containsExactly("active", "deactivated", "archived");
        assertThat(List.of(EntityType.values())).extracting(Enum::name)
                .containsExactly("project", "work_package", "task");

        assertThat(write(ItemStatus.in_progress)).isEqualTo("\"in_progress\"");
        assertThat(write(ProjectStatus.deactivated)).isEqualTo("\"deactivated\"");
        assertThat(write(EntityType.work_package)).isEqualTo("\"work_package\"");
    }

    @Test
    void statusEnumParsingAcceptsTheWireValueCaseInsensitively() {
        assertThat(mapper.readValue("\"BLOCKED\"", ItemStatus.class)).isEqualTo(ItemStatus.blocked);
        assertThat(ItemStatus.parse(" in_progress ")).isEqualTo(ItemStatus.in_progress);
        assertThat(ProjectStatus.parse("ARCHIVED")).isEqualTo(ProjectStatus.archived);
        assertThat(EntityType.parse("work_package")).isEqualTo(EntityType.work_package);
    }

    private String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> keys(String json) {
        return mapper.readValue(json, Map.class);
    }
}
