package de.homelabs.projects.api;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.homelabs.projects.domain.ItemStatus;
import de.homelabs.projects.domain.ItemSummary;
import de.homelabs.projects.domain.WorkPackage;
import de.homelabs.projects.domain.WorkPackageService;

/**
 * Work packages: created inside a project, then read and updated by id or reference
 * ({@code HP-12}). The project of a work package is immutable; status moves freely
 * while the project is not archived.
 */
@RestController
public class WorkPackageController {

    private final WorkPackageService workPackages;

    public WorkPackageController(WorkPackageService workPackages) {
        this.workPackages = workPackages;
    }

    /** Creates a work package inside the project; it takes the project's next reference number. */
    @PostMapping("/api/projects/{idOrCode}/work-packages")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkPackage create(@PathVariable String idOrCode,
            @Valid @RequestBody CreateWorkPackageRequest request,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) {
        return workPackages.create(ApiSupport.caller(jwt, source), idOrCode, new WorkPackageService.Create(
                request.title(), request.description(), request.descriptionHtml(), request.assigneeSub(),
                request.dueDate(), request.labels()));
    }

    /** Lean list across projects; {@code assignee} takes a subject or the literal {@code me}. */
    @GetMapping("/api/work-packages")
    public List<ItemSummary> find(@RequestParam(required = false) String project,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String q,
            @RequestParam(name = "include_inactive_projects", required = false) Boolean includeInactiveProjects,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        List<ItemStatus> statuses = status == null ? List.of() : status.stream().map(ItemStatus::parse).toList();
        return workPackages.find(new WorkPackageService.Filter(project, statuses, assignee, q,
                Boolean.TRUE.equals(includeInactiveProjects),
                ApiSupport.clamp(limit, ApiSupport.DEFAULT_LIMIT, ApiSupport.MAX_LIMIT)), ApiSupport.caller(jwt, null));
    }

    /** Full record including canonical HTML, derived text, labels and the assignee name. */
    @GetMapping("/api/work-packages/{idOrRef}")
    public WorkPackage get(@PathVariable String idOrRef) {
        return workPackages.get(idOrRef);
    }

    /** Partial update: absent fields stay as they are; labels replace the whole set. */
    @PatchMapping("/api/work-packages/{idOrRef}")
    public WorkPackage update(@PathVariable String idOrRef, @Valid @RequestBody UpdateWorkPackageRequest request,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) {
        return workPackages.update(ApiSupport.caller(jwt, source), idOrRef, new WorkPackageService.Update(
                request.title(), request.description(), request.descriptionHtml(), request.status(),
                request.assigneeSub(), request.dueDate(), request.labels()));
    }

    /** Body of {@code POST /api/projects/{idOrCode}/work-packages}. */
    public record CreateWorkPackageRequest(
            @NotBlank @Size(max = 200) String title,
            String description,
            String descriptionHtml,
            String assigneeSub,
            String dueDate,
            List<String> labels) {
    }

    /** Body of {@code PATCH /api/work-packages/{idOrRef}}; absent fields stay unchanged. */
    public record UpdateWorkPackageRequest(
            @Size(max = 200) String title,
            String description,
            String descriptionHtml,
            ItemStatus status,
            String assigneeSub,
            String dueDate,
            List<String> labels) {
    }
}
