package de.homelabs.projects.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.homelabs.projects.domain.ItemStatus;
import de.homelabs.projects.domain.ItemSummary;
import de.homelabs.projects.domain.Task;
import de.homelabs.projects.domain.TaskService;

/**
 * Tasks: always inside a project (given in the body on create), optionally grouped
 * into one of that project's work packages, numbered from the same per-project
 * counter as the work packages — so every reference is unique within its project.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    /** Creates a task; the project comes from the body, the work package is optional. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@Valid @RequestBody CreateTaskRequest request,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) {
        return tasks.create(ApiSupport.caller(jwt, source), new TaskService.Create(
                request.title(), request.description(), request.descriptionHtml(), request.project(),
                request.workPackageId(), request.assigneeSub(), request.dueDate(), request.labels()));
    }

    /** Lean list; {@code assignee} takes a subject or the literal {@code me}. */
    @GetMapping
    public List<ItemSummary> find(@RequestParam(required = false) String project,
            @RequestParam(required = false) String wp,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String assignee,
            @RequestParam(name = "due_before", required = false) LocalDate dueBefore,
            @RequestParam(required = false) String q,
            @RequestParam(name = "include_inactive_projects", required = false) Boolean includeInactiveProjects,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        List<ItemStatus> statuses = status == null ? List.of() : status.stream().map(ItemStatus::parse).toList();
        return tasks.find(new TaskService.Filter(project, wp, statuses, assignee, dueBefore, q,
                Boolean.TRUE.equals(includeInactiveProjects),
                ApiSupport.clamp(limit, ApiSupport.DEFAULT_LIMIT, ApiSupport.MAX_LIMIT)), ApiSupport.caller(jwt, null));
    }

    /** Full record including canonical HTML, derived text, labels and the assignee name. */
    @GetMapping("/{idOrRef}")
    public Task get(@PathVariable String idOrRef) {
        return tasks.get(idOrRef);
    }

    /** Partial update: absent fields stay as they are; labels replace the whole set. */
    @PatchMapping("/{idOrRef}")
    public Task update(@PathVariable String idOrRef, @Valid @RequestBody UpdateTaskRequest request,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) {
        return tasks.update(ApiSupport.caller(jwt, source), idOrRef, new TaskService.Update(
                request.title(), request.description(), request.descriptionHtml(), request.status(),
                request.assigneeSub(), request.dueDate(), request.workPackageId(), request.labels()));
    }

    /** Body of {@code POST /api/tasks}. */
    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            String description,
            String descriptionHtml,
            @NotBlank String project,
            String workPackageId,
            String assigneeSub,
            String dueDate,
            List<String> labels) {
    }

    /** Body of {@code PATCH /api/tasks/{idOrRef}}; absent fields stay unchanged. */
    public record UpdateTaskRequest(
            @Size(max = 200) String title,
            String description,
            String descriptionHtml,
            ItemStatus status,
            String assigneeSub,
            String dueDate,
            String workPackageId,
            List<String> labels) {
    }
}
