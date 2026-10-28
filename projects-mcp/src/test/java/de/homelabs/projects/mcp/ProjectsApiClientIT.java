package de.homelabs.projects.mcp;

import java.util.List;
import java.util.Map;

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
import de.homelabs.projects.mcp.dto.ProjectDto;
import de.homelabs.projects.mcp.dto.ProjectStatus;
import de.homelabs.projects.mcp.dto.ProjectSummaryDto;
import de.homelabs.projects.mcp.dto.TaskDto;
import de.homelabs.projects.mcp.dto.WorkPackageDto;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The adapter's HTTP contract against a fake API: the incoming token is forwarded on
 * every call, the source travels in the internal header, the command bodies carry exactly
 * the API's snake_case field names, the responses are parsed component for component, and
 * the API's error text survives as the tool error.
 *
 * <p>The key-set assertions are the guard against the silent-drop class: the API ignores
 * unknown JSON properties, so a camelCase name would be dropped without an error and the
 * mutation would change nothing.
 */
class ProjectsApiClientIT {

    private static final String INCOMING = "Bearer incoming-harness-token";

    private final FakeProjectsApi fake = new FakeProjectsApi();
    private final JsonMapper mapper = JsonMapper.builder().build();

    private ProjectsApiClient client;

    @BeforeEach
    void setUp() {
        client = new ProjectsApiClient(RestClient.builder(), JsonMapper.builder().build(), fake.baseUrl());
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
    void forwardsTheTokenAndSendsTheSourceHeaderOnEveryWrite() {
        fake.on("POST", "/api/projects", 201, "{}");

        client.createProject("opencode", "Homelabs Platform", "HP", null, null, null);

        FakeProjectsApi.Call call = fake.onlyCall();
        assertThat(call.authorization()).isEqualTo(INCOMING);
        assertThat(call.source()).isEqualTo("opencode");
        assertThat(call.body()).doesNotContain("opencode");
    }

    @Test
    void aBlankSourceStillTravelsAsUnknown() {
        fake.on("POST", "/api/comments", 201, "{}");

        client.createComment(null, EntityType.task, "HP-12", "note");

        assertThat(fake.onlyCall().source()).isEqualTo("unknown");
        assertThat(fake.onlyCall().body()).isEqualTo("{\"parent\":\"task:HP-12\",\"text\":\"note\"}");
    }

    @Test
    void storeProjectSendsExactlyTheWireFields() {
        fake.on("POST", "/api/projects", 201, "{}");

        client.createProject("cli", "Homelabs Platform", "HP", "plain", null, List.of("infra"));

        assertThat(keys(fake.onlyCall())).containsOnlyKeys("name", "short_code", "description", "description_html",
                "labels");
    }

    @Test
    void updateCommandsSendTheWireFieldsAndKeepNullsAsUnchanged() {
        fake.on("PATCH", "/api/projects/11111111-1111-1111-1111-111111111111", 200, "{}");
        fake.on("PATCH", "/api/work-packages/HP-12", 200, WireFixtures.WORK_PACKAGE);
        fake.on("PATCH", "/api/tasks/HP-13", 200, WireFixtures.TASK);

        client.updateProject("cli", "11111111-1111-1111-1111-111111111111", "Renamed", null, null, List.of());
        client.updateWorkPackage("cli", "HP-12", null, null, null, ItemStatus.blocked, "", "", List.of());
        client.updateTask("cli", "HP-13", null, null, null, null, null, null, "", List.of());

        assertThat(keys(fake.call(0))).containsOnlyKeys("name", "description", "description_html", "labels");
        assertThat(keys(fake.call(1))).containsOnlyKeys("title", "description", "description_html", "status",
                "assignee_sub", "due_date", "labels");
        assertThat(keys(fake.call(2))).containsOnlyKeys("title", "description", "description_html", "status",
                "assignee_sub", "due_date", "work_package_id", "labels");
        assertThat(fake.call(1).body()).contains("\"status\":\"blocked\"");
        assertThat(fake.call(2).body()).contains("\"work_package_id\":\"\"");
    }

    @Test
    void storeWorkPackageAndTaskSendTheWireFields() {
        fake.on("POST", "/api/projects/HP/work-packages", 201, WireFixtures.WORK_PACKAGE);
        fake.on("POST", "/api/tasks", 201, WireFixtures.TASK);

        client.createWorkPackage("cli", "HP", "Adapter module", null, null, "user-1", "2026-11-15", List.of());
        client.createTask("cli", "HP", "HP-12", "Write the client", null, null, "user-1", "2026-11-01", List.of());

        assertThat(keys(fake.call(0))).containsOnlyKeys("title", "description", "description_html", "assignee_sub",
                "due_date", "labels");
        assertThat(keys(fake.call(1))).containsOnlyKeys("title", "description", "description_html", "project",
                "work_package_id", "assignee_sub", "due_date", "labels");
    }

    @Test
    void transitionsPostToTheirPathsWithTheSourceHeader() {
        fake.on("POST", "/api/projects/11111111-1111-1111-1111-111111111111/archive", 200, "{}");

        client.transitionProject("cli", "11111111-1111-1111-1111-111111111111", "archive");

        assertThat(fake.onlyCall().path())
                .isEqualTo("/api/projects/11111111-1111-1111-1111-111111111111/archive");
        assertThat(fake.onlyCall().source()).isEqualTo("cli");
    }

    @Test
    void parsesTheFullProjectContract() {
        fake.on("GET", "/api/projects/HP", 200, """
                {"id":"11111111-1111-1111-1111-111111111111","short_code":"HP","name":"Homelabs Platform",
                 "description_html":"<p>Infrastructure work</p>","description_text":"Infrastructure work",
                 "status":"active","owner_sub":"user-1","owner_name":"owner",
                 "created_at":"2026-10-28T09:00:00Z","updated_at":"2026-10-28T09:30:00Z",
                 "labels":[{"id":"22222222-2222-2222-2222-222222222222","name":"infra","color":null}]}""");

        ProjectDto project = client.getProject("HP");

        assertThat(project.id()).isEqualTo(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(project.shortCode()).isEqualTo("HP");
        assertThat(project.name()).isEqualTo("Homelabs Platform");
        assertThat(project.descriptionHtml()).isEqualTo("<p>Infrastructure work</p>");
        assertThat(project.descriptionText()).isEqualTo("Infrastructure work");
        assertThat(project.status()).isEqualTo(ProjectStatus.active);
        assertThat(project.ownerSub()).isEqualTo("user-1");
        assertThat(project.ownerName()).isEqualTo("owner");
        assertThat(project.createdAt()).isEqualTo(java.time.Instant.parse("2026-10-28T09:00:00Z"));
        assertThat(project.updatedAt()).isEqualTo(java.time.Instant.parse("2026-10-28T09:30:00Z"));
        assertThat(project.labels()).singleElement()
                .satisfies(label -> assertThat(label.id())
                        .isEqualTo(java.util.UUID.fromString("22222222-2222-2222-2222-222222222222")));
    }

    @Test
    void parsesTheFullWorkPackageAndTaskContracts() {
        fake.on("GET", "/api/work-packages/HP-12", 200, """
                {"id":"33333333-3333-3333-3333-333333333333","reference":"HP-12",
                 "project_id":"11111111-1111-1111-1111-111111111111","project_short_code":"HP",
                 "title":"Adapter module","description_html":"<p>twenty tools</p>","description_text":"twenty tools",
                 "status":"todo","assignee_sub":"user-1","assignee_name":"owner","due_date":"2026-11-15","seq":12,
                 "created_at":"2026-10-28T09:00:00Z","updated_at":"2026-10-28T09:30:00Z","labels":[]}""");
        fake.on("GET", "/api/tasks/HP-13", 200, """
                {"id":"44444444-4444-4444-4444-444444444444","reference":"HP-13",
                 "project_id":"11111111-1111-1111-1111-111111111111","project_short_code":"HP",
                 "work_package_id":"33333333-3333-3333-3333-333333333333","work_package_reference":"HP-12",
                 "title":"Write the client","description_html":"<p>body</p>","description_text":"body",
                 "status":"in_progress","assignee_sub":"user-1","assignee_name":"owner","due_date":"2026-11-01",
                 "seq":13,"created_at":"2026-10-28T09:00:00Z","updated_at":"2026-10-28T09:40:00Z","labels":[]}""");

        WorkPackageDto workPackage = client.getWorkPackage("HP-12");
        TaskDto task = client.getTask("HP-13");

        assertThat(workPackage.projectShortCode()).isEqualTo("HP");
        assertThat(workPackage.assigneeName()).isEqualTo("owner");
        assertThat(workPackage.dueDate()).isEqualTo(java.time.LocalDate.parse("2026-11-15"));
        assertThat(workPackage.seq()).isEqualTo(12);
        assertThat(task.workPackageReference()).isEqualTo("HP-12");
        assertThat(task.status()).isEqualTo(ItemStatus.in_progress);
        assertThat(task.seq()).isEqualTo(13);
    }

    @Test
    void parsesTheLeanListsAndTheCollaborationContracts() {
        fake.on("GET", "/api/projects", 200, """
                [{"id":"11111111-1111-1111-1111-111111111111","reference":"HP","name":"Homelabs Platform",
                  "status":"active","owner_name":"owner","updated_at":"2026-10-28T09:30:00Z","excerpt":"ex"}]""");
        fake.on("GET", "/api/tasks", 200, """
                [{"id":"44444444-4444-4444-4444-444444444444","reference":"HP-13","title":"Write the client",
                  "status":"done","assignee_sub":null,"assignee_name":null,"due_date":null,"excerpt":"body"}]""");
        fake.on("GET", "/api/activities", 200, """
                [{"id":"66666666-6666-6666-6666-666666666666","entity_type":"task",
                  "entity_id":"44444444-4444-4444-4444-444444444444","actor_sub":"user-1","actor_name":"owner",
                  "source":"opencode","event_type":"status_changed","detail":"todo -> done",
                  "created_at":"2026-10-28T10:00:00Z"}]""");
        fake.on("GET", "/api/attachments", 200, """
                [{"id":"77777777-7777-7777-7777-777777777777","entity_type":"project",
                  "entity_id":"11111111-1111-1111-1111-111111111111","filename":"plan.pdf",
                  "mime_type":"application/pdf","byte_size":2048,"sha256":"abc","uploaded_by_sub":"user-1",
                  "uploaded_by_name":"owner","created_at":"2026-10-28T10:00:00Z"}]""");
        fake.on("GET", "/api/comments", 200, """
                [{"id":"55555555-5555-5555-5555-555555555555","entity_type":"work_package",
                  "entity_id":"33333333-3333-3333-3333-333333333333","author_sub":"user-1",
                  "author_name":"owner","content":"looks good","created_at":"2026-10-28T10:00:00Z"}]""");

        List<ProjectSummaryDto> projects = client.findProjects(List.of(ProjectStatus.active), null, null, null);
        List<ItemSummaryDto> tasks = client.findTasks(null, null, null, null, null, null, null);
        List<ActivityDto> activities = client.listActivities(EntityType.task, "HP-13", null);
        List<AttachmentDto> attachments = client.listAttachments(EntityType.project, "HP", null);
        List<CommentDto> comments = client.listComments(EntityType.work_package, "HP-12", null);

        assertThat(projects).singleElement().satisfies(project -> {
            assertThat(project.reference()).isEqualTo("HP");
            assertThat(project.ownerName()).isEqualTo("owner");
        });
        assertThat(tasks).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(ItemStatus.done);
            assertThat(item.assigneeName()).isNull();
            assertThat(item.dueDate()).isNull();
        });
        assertThat(activities).singleElement().satisfies(entry -> {
            assertThat(entry.entityType()).isEqualTo(EntityType.task);
            assertThat(entry.detail()).isEqualTo("todo -> done");
            assertThat(entry.eventType()).isEqualTo("status_changed");
        });
        assertThat(attachments).singleElement().satisfies(attachment -> {
            assertThat(attachment.byteSize()).isEqualTo(2048);
            assertThat(attachment.uploadedBySub()).isEqualTo("user-1");
        });
        assertThat(comments).singleElement().satisfies(comment -> {
            assertThat(comment.entityType()).isEqualTo(EntityType.work_package);
            assertThat(comment.authorName()).isEqualTo("owner");
        });
    }

    @Test
    void sendsTheFiltersUnderTheApisQueryNames() {
        fake.on("GET", "/api/tasks", 200, "[]");
        fake.on("GET", "/api/projects", 200, "[]");

        client.findTasks("HP", "HP-12", List.of(ItemStatus.todo, ItemStatus.cancelled), "me", "2026-11-30",
                "client", 5);
        client.findProjects(List.of(ProjectStatus.archived), "infra", "platform", 3);

        assertThat(fake.call(0).query()).isEqualTo(
                "project=HP&wp=HP-12&status=todo&status=cancelled&assignee=me&due_before=2026-11-30&q=client&limit=5");
        assertThat(fake.call(1).query()).isEqualTo("status=archived&label=infra&q=platform&limit=3");
    }

    @Test
    void collisionsRenderTheirSuggestionsIntoTheError() {
        fake.on("POST", "/api/projects", 409, """
                {"error":"short code 'HP' is already taken","suggestions":["HPX","HPM"]}""");

        assertThatThrownBy(() -> client.createProject("cli", "Homelabs Platform", "HP", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("short code 'HP' is already taken\nsuggestions: [HPX, HPM]");
    }

    @Test
    void unparseableErrorsFallBackToStatusAndBody() {
        fake.on("GET", "/api/labels", 500, "upstream exploded");

        assertThatThrownBy(() -> client.listLabels())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("projects API answered 500: upstream exploded");
    }

    @Test
    void callsWithoutAnIncomingTokenAreRefused() {
        RequestContextHolder.resetRequestAttributes();

        assertThatThrownBy(() -> client.listLabels())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no incoming Authorization header");
        assertThat(fake.calls()).isEmpty();
    }

    /** The field names of a recorded body, as the API would read them. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> keys(FakeProjectsApi.Call call) {
        return mapper.readValue(call.body(), Map.class);
    }
}
