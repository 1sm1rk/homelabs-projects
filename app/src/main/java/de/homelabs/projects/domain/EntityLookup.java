package de.homelabs.projects.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import de.homelabs.projects.persistence.ProjectRepository;
import de.homelabs.projects.persistence.TaskRepository;
import de.homelabs.projects.persistence.WorkPackageRepository;

/**
 * Resolves the identifier forms the API accepts into entities.
 *
 * <p>Everywhere an identifier is accepted, three spellings work: the UUID, a project
 * short code ({@code HP}) and a short reference ({@code HP-12}). A bare reference is
 * looked up TASKS FIRST and then work packages — the documented tie-break, since both
 * item tables draw from the same per-project counter and a reference is ambiguous
 * only if that invariant is ever broken.
 *
 * <p>Parents of comments, activity and attachments may also be given in the typed
 * form {@code {entity_type}:{id-or-reference}} ({@code task:HP-12}), which is the only
 * unambiguous way to address one by UUID.
 */
@Service
public class EntityLookup {

    /** A resolved parent: the entity plus the project that guards it. */
    public record ResolvedParent(EntityType type, UUID id, UUID projectId, String label) {
    }

    private final ProjectRepository projects;
    private final WorkPackageRepository workPackages;
    private final TaskRepository tasks;

    public EntityLookup(ProjectRepository projects, WorkPackageRepository workPackages, TaskRepository tasks) {
        this.projects = projects;
        this.workPackages = workPackages;
        this.tasks = tasks;
    }

    /** Resolves a project by id or short code; 404 when nothing matches. */
    public Project requireProject(String idOrCode) {
        String value = idOrCode == null ? "" : idOrCode.trim();
        Optional<UUID> id = Values.parseUuid(value);
        if (id.isPresent()) {
            return projects.findById(id.get())
                    .orElseThrow(() -> new NotFoundException("no project " + value));
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("project is required (an id or a short code)");
        }
        return projects.findByShortCode(value)
                .orElseThrow(() -> new NotFoundException("no project " + value));
    }

    /** Resolves a work package by id or reference; 404 when nothing matches. */
    public WorkPackage requireWorkPackage(String idOrRef) {
        String value = idOrRef == null ? "" : idOrRef.trim();
        Optional<UUID> id = Values.parseUuid(value);
        if (id.isPresent()) {
            return workPackages.findById(id.get())
                    .orElseThrow(() -> new NotFoundException("no work package " + value));
        }
        Reference reference = Reference.parse(value)
                .orElseThrow(() -> new NotFoundException("no work package " + value));
        return workPackages.findByReference(reference.code(), reference.seq())
                .orElseThrow(() -> new NotFoundException("no work package " + value));
    }

    /** Resolves a task by id or reference; 404 when nothing matches. */
    public Task requireTask(String idOrRef) {
        String value = idOrRef == null ? "" : idOrRef.trim();
        Optional<UUID> id = Values.parseUuid(value);
        if (id.isPresent()) {
            return tasks.findById(id.get())
                    .orElseThrow(() -> new NotFoundException("no task " + value));
        }
        Reference reference = Reference.parse(value)
                .orElseThrow(() -> new NotFoundException("no task " + value));
        return tasks.findByReference(reference.code(), reference.seq())
                .orElseThrow(() -> new NotFoundException("no task " + value));
    }

    /**
     * Resolves a parent reference: {@code {type}:{id-or-reference}} when typed, a bare
     * {@code CODE-SEQ} (tasks first, then work packages) or a bare project code.
     */
    public ResolvedParent requireParent(String parent) {
        String value = parent == null ? "" : parent.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "parent is required: '<entity_type>:<id-or-reference>', e.g. task:HP-12");
        }
        int colon = value.indexOf(':');
        if (colon > 0) {
            EntityType type = EntityType.parse(value.substring(0, colon));
            String rest = value.substring(colon + 1).trim();
            if (rest.isEmpty()) {
                throw new IllegalArgumentException(
                        "parent must be '<entity_type>:<id-or-reference>', e.g. task:HP-12");
            }
            return switch (type) {
                case project -> {
                    Project project = requireProject(rest);
                    yield new ResolvedParent(EntityType.project, project.id(), project.id(),
                            "project " + project.shortCode());
                }
                case work_package -> {
                    WorkPackage item = requireWorkPackage(rest);
                    yield new ResolvedParent(EntityType.work_package, item.id(), item.projectId(),
                            "work package " + item.reference());
                }
                case task -> {
                    Task item = requireTask(rest);
                    yield new ResolvedParent(EntityType.task, item.id(), item.projectId(),
                            "task " + item.reference());
                }
            };
        }

        Optional<Reference> reference = Reference.parse(value);
        if (reference.isPresent()) {
            Optional<Task> task = tasks.findByReference(reference.get().code(), reference.get().seq());
            if (task.isPresent()) {
                return new ResolvedParent(EntityType.task, task.get().id(), task.get().projectId(),
                        "task " + task.get().reference());
            }
            Optional<WorkPackage> workPackage = workPackages.findByReference(reference.get().code(),
                    reference.get().seq());
            if (workPackage.isPresent()) {
                return new ResolvedParent(EntityType.work_package, workPackage.get().id(),
                        workPackage.get().projectId(), "work package " + workPackage.get().reference());
            }
            throw new NotFoundException("no task or work package " + value);
        }

        Optional<UUID> id = Values.parseUuid(value);
        if (id.isPresent()) {
            Optional<Project> project = projects.findById(id.get());
            if (project.isPresent()) {
                return new ResolvedParent(EntityType.project, project.get().id(), project.get().id(),
                        "project " + project.get().shortCode());
            }
            Optional<WorkPackage> workPackage = workPackages.findById(id.get());
            if (workPackage.isPresent()) {
                return new ResolvedParent(EntityType.work_package, workPackage.get().id(),
                        workPackage.get().projectId(), "work package " + workPackage.get().reference());
            }
            Optional<Task> task = tasks.findById(id.get());
            if (task.isPresent()) {
                return new ResolvedParent(EntityType.task, task.get().id(), task.get().projectId(),
                        "task " + task.get().reference());
            }
            throw new NotFoundException("no project, work package or task " + value);
        }

        Project project = requireProject(value);
        return new ResolvedParent(EntityType.project, project.id(), project.id(),
                "project " + project.shortCode());
    }
}
