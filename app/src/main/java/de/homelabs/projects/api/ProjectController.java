package de.homelabs.projects.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

import de.homelabs.projects.domain.Project;
import de.homelabs.projects.domain.ProjectService;
import de.homelabs.projects.domain.ProjectStatus;
import de.homelabs.projects.domain.ProjectSummary;

/**
 * Projects: create, read, lean list, partial update and the three EXPLICIT lifecycle
 * moves. Status never travels as a PATCH field — {@code deactivate}, {@code activate}
 * and {@code archive} are their own operations.
 *
 * <p>Identifiers accept the UUID, the short code ({@code HP}) or a reference
 * ({@code HP-12}) interchangeably; every reply is a resource record, every error the
 * shared {@code {"error": ...}} shape.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    /** Creates a project; an absent short code is derived from the name. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Project create(@Valid @RequestBody CreateProjectRequest request,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) {
        return projects.create(ApiSupport.caller(jwt, source), new ProjectService.Create(
                request.name(), request.shortCode(), request.description(), request.descriptionHtml(),
                request.labels()));
    }

    /** Lean list, newest activity first; without a status filter only active projects. */
    @GetMapping
    public List<ProjectSummary> find(@RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit) {
        List<ProjectStatus> statuses = status == null
                ? List.of()
                : status.stream().map(ProjectStatus::parse).toList();
        return projects.find(statuses, label, q, ApiSupport.clamp(limit, ApiSupport.DEFAULT_LIMIT, ApiSupport.MAX_LIMIT));
    }

    /** Full record including canonical HTML, derived text and labels. */
    @GetMapping("/{idOrCode}")
    public Project get(@PathVariable String idOrCode) {
        return projects.get(idOrCode);
    }

    /** Partial update: absent fields stay as they are; labels replace the whole set. */
    @PatchMapping("/{idOrCode}")
    public Project update(@PathVariable String idOrCode, @Valid @RequestBody UpdateProjectRequest request,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) {
        return projects.update(ApiSupport.caller(jwt, source), idOrCode, new ProjectService.Update(
                request.name(), request.description(), request.descriptionHtml(), request.labels()));
    }

    /** The reversible "delete": excluded from default lists, still editable. */
    @PostMapping("/{idOrCode}/deactivate")
    public Project deactivate(@PathVariable String idOrCode,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) {
        return projects.transition(ApiSupport.caller(jwt, source), idOrCode, ProjectStatus.deactivated);
    }

    /** Brings a deactivated project back into the normal view. */
    @PostMapping("/{idOrCode}/activate")
    public Project activate(@PathVariable String idOrCode,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) {
        return projects.transition(ApiSupport.caller(jwt, source), idOrCode, ProjectStatus.active);
    }

    /** Finishes a project: the whole subtree becomes read-only (later writes answer 409). */
    @PostMapping("/{idOrCode}/archive")
    public Project archive(@PathVariable String idOrCode,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) {
        return projects.transition(ApiSupport.caller(jwt, source), idOrCode, ProjectStatus.archived);
    }

    /** Body of {@code POST /api/projects}; exactly one of the content shapes may be used. */
    public record CreateProjectRequest(
            @NotBlank @Size(max = 200) String name,
            @Pattern(regexp = "^[A-Za-z0-9]{2,6}$",
                    message = "must be 2-6 characters of A-Z and 0-9") String shortCode,
            String description,
            String descriptionHtml,
            List<String> labels) {
    }

    /** Body of {@code PATCH /api/projects/{idOrCode}}; absent fields stay unchanged. */
    public record UpdateProjectRequest(
            @Size(max = 200) String name,
            String description,
            String descriptionHtml,
            List<String> labels) {
    }
}
