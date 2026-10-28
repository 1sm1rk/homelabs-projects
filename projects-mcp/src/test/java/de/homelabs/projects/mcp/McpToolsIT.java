package de.homelabs.projects.mcp;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tool → REST chain end to end: every tool calls the fake API with the incoming
 * harness token and the MCP client identity, its parameters land where the API expects
 * them, and the replies carry the record the API returned.
 *
 * <p>The body assertions are the important part: the API ignores unknown JSON
 * properties, so a camelCase field name would be dropped silently and a mutation would
 * change nothing. Each write therefore asserts the snake_case names explicitly.
 */
class McpToolsIT {

    private static final String INCOMING = "Bearer incoming-harness-token";

    private final FakeProjectsApi fake = new FakeProjectsApi();

    private ProjectTools projectTools;
    private WorkItemTools workItemTools;
    private CollaborationTools collaborationTools;

    @BeforeEach
    void setUp() {
        ProjectsApiClient api = new ProjectsApiClient(RestClient.builder(), JsonMapper.builder().build(),
                fake.baseUrl());
        projectTools = new ProjectTools(api);
        workItemTools = new WorkItemTools(api);
        collaborationTools = new CollaborationTools(api);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", INCOMING);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        fake.close();
    }

    @Test
    void storeProjectForwardsTheClientIdentityAndSendsWireFieldNames() {
        fake.on("POST", "/api/projects", 201, WireFixtures.PROJECT);

        ProjectDto created = projectTools.store_project(exchange("opencode"), "Homelabs Platform", "HP",
                "Infrastructure work", null, List.of("infra"));

        assertThat(created.shortCode()).isEqualTo("HP");
        assertThat(created.descriptionText()).isEqualTo("Infrastructure work");
        assertThat(created.status()).isEqualTo(ProjectStatus.active);
        assertThat(created.labels()).singleElement().satisfies(label -> assertThat(label.name()).isEqualTo("infra"));

        FakeProjectsApi.Call call = fake.onlyCall();
        assertThat(call.method()).isEqualTo("POST");
        assertThat(call.path()).isEqualTo("/api/projects");
        assertThat(call.authorization()).isEqualTo(INCOMING);
        assertThat(call.source()).isEqualTo("opencode");
        assertThat(call.body())
                .contains("\"name\":\"Homelabs Platform\"")
                .contains("\"short_code\":\"HP\"")
                .doesNotContain("shortCode")
                .doesNotContain("descriptionHtml")
                .doesNotContain("opencode");
    }

    @Test
    void missingClientInfoFallsBackToUnknown() {
        fake.on("POST", "/api/projects", 201, WireFixtures.PROJECT);

        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        when(exchange.getClientInfo()).thenReturn(null);

        projectTools.store_project(exchange, "Homelabs Platform", null, null, null, null);

        assertThat(fake.onlyCall().source()).isEqualTo("unknown");
    }

    @Test
    void getProjectAddressesTheIdentifierPath() {
        fake.on("GET", "/api/projects/HP", 200, WireFixtures.PROJECT);

        ProjectDto project = projectTools.get_project("HP");

        assertThat(project.id()).isEqualTo(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(project.ownerSub()).isEqualTo("user-1");
        assertThat(project.createdAt()).isNotNull();
        assertThat(fake.onlyCall().authorization()).isEqualTo(INCOMING);
    }

    @Test
    void findProjectsSendsTheFiltersAndParsesLeanSummaries() {
        fake.on("GET", "/api/projects", 200, """
                [{"id":"11111111-1111-1111-1111-111111111111","reference":"HP","name":"Homelabs Platform",
                  "status":"deactivated","owner_name":"owner","updated_at":"2026-10-28T09:30:00Z",
                  "excerpt":"Infrastructure work"}]""");

        List<ProjectSummaryDto> projects = projectTools.find_projects(
                List.of(ProjectStatus.deactivated, ProjectStatus.archived), "infra", "platform", 5);

        assertThat(projects).singleElement().satisfies(project -> {
            assertThat(project.reference()).isEqualTo("HP");
            assertThat(project.status()).isEqualTo(ProjectStatus.deactivated);
            assertThat(project.excerpt()).isEqualTo("Infrastructure work");
        });
        assertThat(fake.onlyCall().query())
                .contains("status=deactivated")
                .contains("status=archived")
                .contains("label=infra")
                .contains("q=platform")
                .contains("limit=5");
    }

    @Test
    void updateProjectSendsAPatchWithExplicitWireNames() {
        fake.on("PATCH", "/api/projects/11111111-1111-1111-1111-111111111111", 200, WireFixtures.PROJECT);

        projectTools.update_project(exchange("cli"), "11111111-1111-1111-1111-111111111111", "Renamed", null,
                "<p>new</p>", List.of());

        FakeProjectsApi.Call call = fake.onlyCall();
        assertThat(call.method()).isEqualTo("PATCH");
        assertThat(call.body()).contains("\"name\":\"Renamed\"")
                .contains("\"description_html\":\"<p>new</p>\"")
                .contains("\"labels\":[]")
                .doesNotContain("descriptionHtml");
        assertThat(call.source()).isEqualTo("cli");
    }

    @Test
    void lifecycleMovesPostToTheirOwnPaths() {
        String id = "11111111-1111-1111-1111-111111111111";
        fake.on("POST", "/api/projects/" + id + "/deactivate", 200, WireFixtures.PROJECT);
        fake.on("POST", "/api/projects/" + id + "/activate", 200, WireFixtures.PROJECT);
        fake.on("POST", "/api/projects/" + id + "/archive", 200, WireFixtures.PROJECT);

        projectTools.deactivate_project(exchange("cli"), id);
        projectTools.activate_project(exchange("cli"), id);
        projectTools.archive_project(exchange("cli"), id);

        assertThat(fake.calls()).extracting(FakeProjectsApi.Call::method).containsOnly("POST");
        assertThat(fake.calls()).extracting(FakeProjectsApi.Call::path).containsExactly(
                "/api/projects/" + id + "/deactivate",
                "/api/projects/" + id + "/activate",
                "/api/projects/" + id + "/archive");
    }

    @Test
    void storeWorkPackageCreatesInsideTheProjectPath() {
        fake.on("POST", "/api/projects/HP/work-packages", 201, WireFixtures.WORK_PACKAGE);

        WorkPackageDto created = workItemTools.store_work_package(exchange("cli"), "HP", "Adapter module",
                "twenty tools", null, null, "2026-11-15", null);

        assertThat(created.reference()).isEqualTo("HP-12");
        assertThat(created.status()).isEqualTo(ItemStatus.todo);
        assertThat(created.dueDate()).isEqualTo(java.time.LocalDate.parse("2026-11-15"));

        FakeProjectsApi.Call call = fake.onlyCall();
        assertThat(call.path()).isEqualTo("/api/projects/HP/work-packages");
        assertThat(call.body()).contains("\"title\":\"Adapter module\"")
                .contains("\"due_date\":\"2026-11-15\"")
                .doesNotContain("\"project\"")
                .doesNotContain("dueDate");
    }

    @Test
    void getWorkPackageIsAddressedByReference() {
        fake.on("GET", "/api/work-packages/HP-12", 200, WireFixtures.WORK_PACKAGE);

        WorkPackageDto item = workItemTools.get_work_package("HP-12");

        assertThat(item.reference()).isEqualTo("HP-12");
        assertThat(item.projectShortCode()).isEqualTo("HP");
        assertThat(item.seq()).isEqualTo(12);
        assertThat(fake.onlyCall().path()).isEqualTo("/api/work-packages/HP-12");
    }

    @Test
    void findWorkPackagesSendsRepeatedStatusesAndFilters() {
        fake.on("GET", "/api/work-packages", 200, """
                [{"id":"33333333-3333-3333-3333-333333333333","reference":"HP-12","title":"Adapter module",
                  "status":"blocked","assignee_sub":"user-1","assignee_name":"owner","due_date":"2026-11-15",
                  "excerpt":"twenty tools"}]""");

        List<ItemSummaryDto> items = workItemTools.find_work_packages("HP",
                List.of(ItemStatus.todo, ItemStatus.blocked), "me", "adapter", 10);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(ItemStatus.blocked);
            assertThat(item.assigneeName()).isEqualTo("owner");
        });
        assertThat(fake.onlyCall().query())
                .contains("project=HP")
                .contains("status=todo")
                .contains("status=blocked")
                .contains("assignee=me")
                .contains("q=adapter")
                .contains("limit=10");
    }

    @Test
    void updateWorkPackageSendsTheWireStatusAndTheTriState() {
        fake.on("PATCH", "/api/work-packages/HP-12", 200, WireFixtures.WORK_PACKAGE);

        workItemTools.update_work_package(exchange("cli"), "HP-12", null, null, null,
                ItemStatus.in_progress, "", "", List.of());

        FakeProjectsApi.Call call = fake.onlyCall();
        assertThat(call.method()).isEqualTo("PATCH");
        assertThat(call.body())
                .contains("\"status\":\"in_progress\"")
                .contains("\"assignee_sub\":\"\"")
                .contains("\"due_date\":\"\"")
                .contains("\"labels\":[]")
                .doesNotContain("IN_PROGRESS");
    }

    @Test
    void storeTaskSendsProjectAndWorkPackageInTheBody() {
        fake.on("POST", "/api/tasks", 201, WireFixtures.TASK);

        TaskDto created = workItemTools.store_task(exchange("cli"), "HP", "HP-12", "Write the client",
                "body", null, "user-1", "2026-11-01", List.of("infra"));

        assertThat(created.reference()).isEqualTo("HP-13");
        assertThat(created.workPackageReference()).isEqualTo("HP-12");
        assertThat(created.assigneeName()).isEqualTo("owner");

        FakeProjectsApi.Call call = fake.onlyCall();
        assertThat(call.path()).isEqualTo("/api/tasks");
        assertThat(call.body())
                .contains("\"project\":\"HP\"")
                .contains("\"work_package_id\":\"HP-12\"")
                .contains("\"assignee_sub\":\"user-1\"")
                .contains("\"due_date\":\"2026-11-01\"")
                .doesNotContain("workPackageId")
                .doesNotContain("assigneeSub");
    }

    @Test
    void getTaskParsesTheFullRecord() {
        fake.on("GET", "/api/tasks/HP-13", 200, WireFixtures.TASK);

        TaskDto task = workItemTools.get_task("HP-13");

        assertThat(task.id()).isEqualTo(java.util.UUID.fromString("44444444-4444-4444-4444-444444444444"));
        assertThat(task.workPackageId()).isEqualTo(java.util.UUID.fromString("33333333-3333-3333-3333-333333333333"));
        assertThat(task.descriptionHtml()).isEqualTo("<p>body</p>");
        assertThat(task.labels()).singleElement().satisfies(label -> assertThat(label.name()).isEqualTo("infra"));
    }

    @Test
    void findTasksSendsDueBeforeAndTheMeFilter() {
        fake.on("GET", "/api/tasks", 200, """
                [{"id":"44444444-4444-4444-4444-444444444444","reference":"HP-13","title":"Write the client",
                  "status":"in_progress","assignee_sub":"user-1","assignee_name":"owner",
                  "due_date":"2026-11-01","excerpt":"body"}]""");

        List<ItemSummaryDto> tasks = workItemTools.find_tasks("HP", "HP-12",
                List.of(ItemStatus.todo, ItemStatus.in_progress), "me", "2026-11-30", "client", 5);

        assertThat(tasks).singleElement().satisfies(task -> assertThat(task.dueDate())
                .isEqualTo(java.time.LocalDate.parse("2026-11-01")));
        assertThat(fake.onlyCall().query())
                .contains("project=HP")
                .contains("wp=HP-12")
                .contains("status=todo")
                .contains("status=in_progress")
                .contains("assignee=me")
                .contains("due_before=2026-11-30")
                .contains("q=client")
                .contains("limit=5");
    }

    @Test
    void updateTaskClearsTheGroupingWithAnEmptyString() {
        fake.on("PATCH", "/api/tasks/HP-13", 200, WireFixtures.TASK);

        workItemTools.update_task(exchange("cli"), "HP-13", "Retitled", null, null, ItemStatus.done, "",
                null, null, null);

        FakeProjectsApi.Call call = fake.onlyCall();
        assertThat(call.method()).isEqualTo("PATCH");
        assertThat(call.path()).isEqualTo("/api/tasks/HP-13");
        assertThat(call.body())
                .contains("\"title\":\"Retitled\"")
                .contains("\"status\":\"done\"")
                .contains("\"work_package_id\":\"\"")
                .contains("\"assignee_sub\":null");
    }

    @Test
    void storeCommentComposesTheTypedParent() {
        fake.on("POST", "/api/comments", 201, """
                {"id":"55555555-5555-5555-5555-555555555555","entity_type":"task",
                 "entity_id":"44444444-4444-4444-4444-444444444444","author_sub":"user-1",
                 "author_name":"owner","content":"looks good","created_at":"2026-10-28T10:00:00Z"}""");

        CommentDto comment = collaborationTools.store_comment(exchange("opencode"), EntityType.task, "HP-12",
                "looks good");

        assertThat(comment.entityType()).isEqualTo(EntityType.task);
        assertThat(comment.authorName()).isEqualTo("owner");

        FakeProjectsApi.Call call = fake.onlyCall();
        assertThat(call.body()).contains("\"parent\":\"task:HP-12\"").contains("\"text\":\"looks good\"");
        assertThat(call.source()).isEqualTo("opencode");
    }

    @Test
    void findCommentsUsesTheParentQuery() {
        fake.on("GET", "/api/comments", 200, """
                [{"id":"55555555-5555-5555-5555-555555555555","entity_type":"project",
                  "entity_id":"11111111-1111-1111-1111-111111111111","author_sub":"user-1",
                  "author_name":"owner","content":"first","created_at":"2026-10-28T10:00:00Z"}]""");

        List<CommentDto> comments = collaborationTools.find_comments(EntityType.project, "HP", 20);

        assertThat(comments).singleElement().satisfies(comment -> {
            assertThat(comment.entityType()).isEqualTo(EntityType.project);
            assertThat(comment.content()).isEqualTo("first");
        });
        assertThat(fake.onlyCall().query()).contains("parent=project:HP").contains("limit=20");
    }

    @Test
    void getActivityUsesTheParentQueryAndLimit() {
        fake.on("GET", "/api/activities", 200, """
                [{"id":"66666666-6666-6666-6666-666666666666","entity_type":"task",
                  "entity_id":"44444444-4444-4444-4444-444444444444","actor_sub":"user-1",
                  "actor_name":"owner","source":"opencode","event_type":"status_changed",
                  "detail":"todo -> in_progress","created_at":"2026-10-28T10:00:00Z"}]""");

        List<ActivityDto> entries = collaborationTools.get_activity(EntityType.task, "HP-13", 10);

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.eventType()).isEqualTo("status_changed");
            assertThat(entry.source()).isEqualTo("opencode");
            assertThat(entry.actorSub()).isEqualTo("user-1");
        });
        assertThat(fake.onlyCall().query()).contains("parent=task:HP-13").contains("limit=10");
    }

    @Test
    void listLabelsParsesTheVocabulary() {
        fake.on("GET", "/api/labels", 200, """
                [{"id":"22222222-2222-2222-2222-222222222222","name":"infra","color":null}]""");

        List<LabelDto> labels = collaborationTools.list_labels();

        assertThat(labels).singleElement().satisfies(label -> {
            assertThat(label.name()).isEqualTo("infra");
            assertThat(label.color()).isNull();
        });
        assertThat(fake.onlyCall().path()).isEqualTo("/api/labels");
        assertThat(fake.onlyCall().authorization()).isEqualTo(INCOMING);
    }

    @Test
    void listAttachmentsReturnsMetadataOnly() {
        fake.on("GET", "/api/attachments", 200, """
                [{"id":"77777777-7777-7777-7777-777777777777","entity_type":"project",
                  "entity_id":"11111111-1111-1111-1111-111111111111","filename":"plan.pdf",
                  "mime_type":"application/pdf","byte_size":2048,"sha256":"abc",
                  "uploaded_by_sub":"user-1","uploaded_by_name":"owner","created_at":"2026-10-28T10:00:00Z"}]""");

        List<AttachmentDto> attachments = collaborationTools.list_attachments(EntityType.project, "HP");

        assertThat(attachments).singleElement().satisfies(attachment -> {
            assertThat(attachment.filename()).isEqualTo("plan.pdf");
            assertThat(attachment.mimeType()).isEqualTo("application/pdf");
            assertThat(attachment.byteSize()).isEqualTo(2048);
            assertThat(attachment.uploadedByName()).isEqualTo("owner");
        });
        assertThat(fake.onlyCall().query()).isEqualTo("parent=project:HP");
    }

    @Test
    void shortCodeCollisionsSurfaceTheSuggestions() {
        fake.on("POST", "/api/projects", 409, """
                {"error":"short code 'HP' is already taken","suggestions":["HPX","HPM","HP2"]}""");

        assertThatThrownBy(() -> projectTools.store_project(exchange("cli"), "Homelabs Platform", "HP",
                null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("short code 'HP' is already taken\nsuggestions: [HPX, HPM, HP2]");
    }

    @Test
    void otherErrorShapesStayUntouched() {
        fake.on("GET", "/api/projects/UNKNOWN", 404, """
                {"error":"no project UNKNOWN"}""");
        fake.on("PATCH", "/api/tasks/HP-13", 409, """
                {"error":"project HP is archived: the project and everything under it is read-only"}""");
        fake.on("POST", "/api/tasks", 400, """
                {"error":"title: must not be blank"}""");

        assertThatThrownBy(() -> projectTools.get_project("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no project UNKNOWN");
        assertThatThrownBy(() -> workItemTools.update_task(exchange("cli"), "HP-13", "x", null, null, null,
                null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("project HP is archived: the project and everything under it is read-only");
        assertThatThrownBy(() -> workItemTools.store_task(exchange("cli"), "HP", null, "", null, null, null,
                null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title: must not be blank");
    }

    @Test
    void callsWithoutAnIncomingTokenAreRefused() {
        RequestContextHolder.resetRequestAttributes();

        assertThatThrownBy(() -> projectTools.get_project("HP"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no incoming Authorization header");
        assertThat(fake.calls()).isEmpty();
    }

    private McpSyncServerExchange exchange(String clientName) {
        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation(clientName, "1.0.0"));
        return exchange;
    }
}
