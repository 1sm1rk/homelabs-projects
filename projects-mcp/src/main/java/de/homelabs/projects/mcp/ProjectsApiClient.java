package de.homelabs.projects.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriBuilder;

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
import tools.jackson.databind.ObjectMapper;

/**
 * Typed client for the homelabs-projects REST API: one method per tool, one HTTP call per
 * method. It compiles against the JSON contract only — no service classes are on its
 * classpath, which is what keeps the adapter a separate artifact, and it never touches
 * the database.
 *
 * <p><strong>Token forwarding.</strong> Every call copies the INCOMING harness token —
 * the {@code Authorization} header of the MCP request being served — verbatim onto the
 * API call. The gateway validated that token before invoking the tool; the API validates
 * it again (issuer + audience) and derives {@code owner} from its {@code sub}. The
 * subject is never sent as a parameter, so an agent cannot act as another user. The
 * token is read per call (no caching) and never logged.
 *
 * <p><strong>Source header.</strong> Write calls carry the MCP client identity in the
 * {@code X-Mcp-Source} header. The value is derived in the tool layer from the MCP
 * initialize handshake ({@code clientInfo}), never from tool arguments: the API accepts
 * {@code source} only from this header on authenticated calls and never from a request
 * body field.
 *
 * <p><strong>Wire names.</strong> The API speaks snake_case. Every outbound command
 * record below names its fields explicitly instead of relying on the mapper's naming
 * strategy, because the mapper belongs to whichever JVM composes the adapter.
 */
@Component
public class ProjectsApiClient {

    /**
     * Internal header carrying the MCP client identity from the adapter to the API.
     * The API side reads it as the only accepted {@code source} input.
     */
    static final String SOURCE_HEADER = "X-Mcp-Source";

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public ProjectsApiClient(RestClient.Builder builder, ObjectMapper objectMapper,
            @Value("${projects.mcp.api.base-url:http://localhost:8090}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    // --- projects --------------------------------------------------------------

    ProjectDto createProject(String source, String name, String shortCode, String description,
            String descriptionHtml, List<String> labels) {
        return body(write(HttpMethod.POST, source, "/api/projects")
                .body(new StoreProjectCommand(name, shortCode, description, descriptionHtml, labels)),
                ProjectDto.class);
    }

    ProjectDto getProject(String idOrCode) {
        return body(client.get().uri("/api/projects/{idOrCode}", idOrCode), ProjectDto.class);
    }

    List<ProjectSummaryDto> findProjects(List<ProjectStatus> statuses, String label, String query, Integer limit) {
        return body(client.get()
                .uri(uri -> {
                    uri.path("/api/projects");
                    repeated(uri, "status", projectStatuses(statuses));
                    uri.queryParamIfPresent("label", Optional.ofNullable(label));
                    uri.queryParamIfPresent("q", Optional.ofNullable(query));
                    uri.queryParamIfPresent("limit", Optional.ofNullable(limit));
                    return uri.build();
                }), new ParameterizedTypeReference<List<ProjectSummaryDto>>() {
                });
    }

    ProjectDto updateProject(String source, String id, String name, String description, String descriptionHtml,
            List<String> labels) {
        return body(write(HttpMethod.PATCH, source, "/api/projects/{id}", id)
                .body(new UpdateProjectCommand(name, description, descriptionHtml, labels)), ProjectDto.class);
    }

    ProjectDto transitionProject(String source, String id, String transition) {
        return body(write(HttpMethod.POST, source, "/api/projects/{id}/" + transition, id), ProjectDto.class);
    }

    // --- work packages ---------------------------------------------------------

    WorkPackageDto createWorkPackage(String source, String projectRef, String title, String description,
            String descriptionHtml, String assigneeSub, String dueDate, List<String> labels) {
        return body(write(HttpMethod.POST, source, "/api/projects/{projectRef}/work-packages", projectRef)
                .body(new StoreWorkPackageCommand(title, description, descriptionHtml, assigneeSub, dueDate,
                        labels)), WorkPackageDto.class);
    }

    WorkPackageDto getWorkPackage(String idOrRef) {
        return body(client.get().uri("/api/work-packages/{idOrRef}", idOrRef), WorkPackageDto.class);
    }

    List<ItemSummaryDto> findWorkPackages(String project, List<ItemStatus> statuses, String assignee, String query,
            Integer limit) {
        return body(client.get()
                .uri(uri -> {
                    uri.path("/api/work-packages");
                    uri.queryParamIfPresent("project", Optional.ofNullable(project));
                    repeated(uri, "status", itemStatuses(statuses));
                    uri.queryParamIfPresent("assignee", Optional.ofNullable(assignee));
                    uri.queryParamIfPresent("q", Optional.ofNullable(query));
                    uri.queryParamIfPresent("limit", Optional.ofNullable(limit));
                    return uri.build();
                }), new ParameterizedTypeReference<List<ItemSummaryDto>>() {
                });
    }

    WorkPackageDto updateWorkPackage(String source, String idOrRef, String title, String description,
            String descriptionHtml, ItemStatus status, String assigneeSub, String dueDate, List<String> labels) {
        return body(write(HttpMethod.PATCH, source, "/api/work-packages/{idOrRef}", idOrRef)
                .body(new UpdateWorkPackageCommand(title, description, descriptionHtml, status, assigneeSub,
                        dueDate, labels)), WorkPackageDto.class);
    }

    // --- tasks -----------------------------------------------------------------

    TaskDto createTask(String source, String project, String workPackageId, String title, String description,
            String descriptionHtml, String assigneeSub, String dueDate, List<String> labels) {
        return body(write(HttpMethod.POST, source, "/api/tasks")
                .body(new StoreTaskCommand(title, description, descriptionHtml, project, workPackageId, assigneeSub,
                        dueDate, labels)), TaskDto.class);
    }

    TaskDto getTask(String idOrRef) {
        return body(client.get().uri("/api/tasks/{idOrRef}", idOrRef), TaskDto.class);
    }

    List<ItemSummaryDto> findTasks(String project, String workPackage, List<ItemStatus> statuses, String assignee,
            String dueBefore, String query, Integer limit) {
        return body(client.get()
                .uri(uri -> {
                    uri.path("/api/tasks");
                    uri.queryParamIfPresent("project", Optional.ofNullable(project));
                    uri.queryParamIfPresent("wp", Optional.ofNullable(workPackage));
                    repeated(uri, "status", itemStatuses(statuses));
                    uri.queryParamIfPresent("assignee", Optional.ofNullable(assignee));
                    uri.queryParamIfPresent("due_before", Optional.ofNullable(dueBefore));
                    uri.queryParamIfPresent("q", Optional.ofNullable(query));
                    uri.queryParamIfPresent("limit", Optional.ofNullable(limit));
                    return uri.build();
                }), new ParameterizedTypeReference<List<ItemSummaryDto>>() {
                });
    }

    TaskDto updateTask(String source, String idOrRef, String title, String description, String descriptionHtml,
            ItemStatus status, String assigneeSub, String dueDate, String workPackageId, List<String> labels) {
        return body(write(HttpMethod.PATCH, source, "/api/tasks/{idOrRef}", idOrRef)
                .body(new UpdateTaskCommand(title, description, descriptionHtml, status, assigneeSub, dueDate,
                        workPackageId, labels)), TaskDto.class);
    }

    // --- comments, activity, labels, attachments -------------------------------

    CommentDto createComment(String source, EntityType entityType, String entityId, String text) {
        return body(write(HttpMethod.POST, source, "/api/comments")
                .body(new StoreCommentCommand(ParentRef.compose(entityType, entityId), text)), CommentDto.class);
    }

    List<CommentDto> listComments(EntityType entityType, String entityId, Integer limit) {
        String parent = ParentRef.compose(entityType, entityId);
        return body(client.get()
                .uri(uri -> uri.path("/api/comments")
                        .queryParam("parent", parent)
                        .queryParamIfPresent("limit", Optional.ofNullable(limit))
                        .build()), new ParameterizedTypeReference<List<CommentDto>>() {
                });
    }

    List<ActivityDto> listActivities(EntityType entityType, String entityId, Integer limit) {
        String parent = ParentRef.compose(entityType, entityId);
        return body(client.get()
                .uri(uri -> uri.path("/api/activities")
                        .queryParam("parent", parent)
                        .queryParamIfPresent("limit", Optional.ofNullable(limit))
                        .build()), new ParameterizedTypeReference<List<ActivityDto>>() {
                });
    }

    List<LabelDto> listLabels() {
        return body(client.get().uri("/api/labels"), new ParameterizedTypeReference<List<LabelDto>>() {
        });
    }

    List<AttachmentDto> listAttachments(EntityType entityType, String entityId, Integer limit) {
        String parent = ParentRef.compose(entityType, entityId);
        return body(client.get()
                .uri(uri -> uri.path("/api/attachments")
                        .queryParam("parent", parent)
                        .queryParamIfPresent("limit", Optional.ofNullable(limit))
                        .build()), new ParameterizedTypeReference<List<AttachmentDto>>() {
                });
    }

    // --- plumbing --------------------------------------------------------------

    /** Write call with the source header; the token is added by {@link #forwardToken}. */
    private RestClient.RequestBodySpec write(HttpMethod method, String source, String path, Object... uriVariables) {
        return client.method(method).uri(path, uriVariables)
                .header(SOURCE_HEADER, source == null || source.isBlank() ? "unknown" : source);
    }

    /** Appends one repeated query parameter per value; no values means "no filter". */
    private static void repeated(UriBuilder uri, String name, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        uri.queryParam(name, values.toArray());
    }

    /** The wire strings of the given item statuses; a null or empty list filters nothing. */
    private static List<String> itemStatuses(List<ItemStatus> statuses) {
        return statuses == null ? List.of() : statuses.stream().map(ItemStatus::value).toList();
    }

    /** The wire strings of the given project statuses; a null or empty list filters nothing. */
    private static List<String> projectStatuses(List<ProjectStatus> statuses) {
        return statuses == null ? List.of() : statuses.stream().map(ProjectStatus::value).toList();
    }

    /**
     * Copies the incoming {@code Authorization} header onto the outgoing call. Reading
     * the raw header (instead of a security-context object) keeps the adapter free of any
     * auth framework: whoever authorized the MCP request already validated the token, and
     * the API validates it again.
     */
    private void forwardToken(HttpHeaders headers) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            String authorization = servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
                return;
            }
        }
        throw new IllegalStateException("no incoming Authorization header to forward to the projects API");
    }

    private <T> T body(RestClient.RequestHeadersSpec<?> spec, Class<T> type) {
        return errorAware(spec.headers(this::forwardToken).retrieve()).body(type);
    }

    private <T> T body(RestClient.RequestHeadersSpec<?> spec, ParameterizedTypeReference<T> type) {
        return errorAware(spec.headers(this::forwardToken).retrieve()).body(type);
    }

    private RestClient.ResponseSpec errorAware(RestClient.ResponseSpec spec) {
        return spec.onStatus(status -> status.isError(), (request, response) -> {
            throw new IllegalArgumentException(
                    ApiErrors.message(objectMapper, response.getStatusCode().value(), readBody(response)));
        });
    }

    private String readBody(ClientHttpResponse response) {
        try (InputStream stream = response.getBody()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    // --- outbound payloads (the API's request contract) -------------------------
    //
    // Every field whose Java name differs from the snake_case wire name carries an
    // explicit @JsonProperty. The API ignores unknown properties, so a camelCase field
    // name would be dropped silently and the mutation would change nothing.

    /** Body of {@code POST /api/projects}. */
    record StoreProjectCommand(
            String name,
            @JsonProperty("short_code") String shortCode,
            String description,
            @JsonProperty("description_html") String descriptionHtml,
            List<String> labels) {
    }

    /** Body of {@code PATCH /api/projects/{id}}; {@code null} means "unchanged". */
    record UpdateProjectCommand(
            String name,
            String description,
            @JsonProperty("description_html") String descriptionHtml,
            List<String> labels) {
    }

    /** Body of {@code POST /api/projects/{idOrCode}/work-packages}. */
    record StoreWorkPackageCommand(
            String title,
            String description,
            @JsonProperty("description_html") String descriptionHtml,
            @JsonProperty("assignee_sub") String assigneeSub,
            @JsonProperty("due_date") String dueDate,
            List<String> labels) {
    }

    /** Body of {@code PATCH /api/work-packages/{idOrRef}}; {@code null} means "unchanged". */
    record UpdateWorkPackageCommand(
            String title,
            String description,
            @JsonProperty("description_html") String descriptionHtml,
            ItemStatus status,
            @JsonProperty("assignee_sub") String assigneeSub,
            @JsonProperty("due_date") String dueDate,
            List<String> labels) {
    }

    /** Body of {@code POST /api/tasks}; the project comes from the body. */
    record StoreTaskCommand(
            String title,
            String description,
            @JsonProperty("description_html") String descriptionHtml,
            String project,
            @JsonProperty("work_package_id") String workPackageId,
            @JsonProperty("assignee_sub") String assigneeSub,
            @JsonProperty("due_date") String dueDate,
            List<String> labels) {
    }

    /** Body of {@code PATCH /api/tasks/{idOrRef}}; {@code null} means "unchanged". */
    record UpdateTaskCommand(
            String title,
            String description,
            @JsonProperty("description_html") String descriptionHtml,
            ItemStatus status,
            @JsonProperty("assignee_sub") String assigneeSub,
            @JsonProperty("due_date") String dueDate,
            @JsonProperty("work_package_id") String workPackageId,
            List<String> labels) {
    }

    /** Body of {@code POST /api/comments}; {@code parent} is the typed entity reference. */
    record StoreCommentCommand(String parent, String text) {
    }
}
