package de.homelabs.projects.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.homelabs.projects.persistence.ProjectRepository;

/**
 * Project lifecycle and content.
 *
 * <p>Status is never a free PATCH field: the three moves ({@code deactivate},
 * {@code activate}, {@code archive}) are explicit operations with a guard, and
 * {@code archived} is a hard stop — every write path of the subtree asks
 * {@link #requireWritable} first and answers 409, including children and comments.
 *
 * <p>The short code is written once and never changes (references must keep their
 * meaning): a collision is answered with alternative codes so an agent retries
 * instead of guessing.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final EntityLookup lookup;
    private final LabelService labels;
    private final ActivityService activities;

    public ProjectService(ProjectRepository projects, EntityLookup lookup, LabelService labels,
            ActivityService activities) {
        this.projects = projects;
        this.lookup = lookup;
        this.labels = labels;
        this.activities = activities;
    }

    /** Input of {@code POST /api/projects}; exactly one content shape may be supplied. */
    public record Create(String name, String shortCode, String description, String descriptionHtml,
            List<String> labels) {
    }

    /** Input of {@code PATCH /api/projects/{id}}: {@code null} means "leave unchanged". */
    public record Update(String name, String description, String descriptionHtml, List<String> labels) {
    }

    @Transactional
    public Project create(Caller caller, Create command) {
        String shortCode = resolveShortCode(command.name(), command.shortCode());
        RichText.Content content = RichText.canonicalize(command.description(), command.descriptionHtml());

        UUID id = UUID.randomUUID();
        projects.insert(id, shortCode, command.name().strip(), content.html(), content.text(), caller.sub(),
                caller.username());
        Project created = projects.findById(id).orElseThrow();
        activities.record(caller, EntityType.project, id, ActivityService.CREATED, null);

        List<Label> assigned = List.of();
        if (command.labels() != null) {
            LabelService.Assignment assignment = labels.assign(EntityType.project, id, command.labels());
            assigned = assignment.labels();
            if (!assignment.added().isEmpty()) {
                activities.record(caller, EntityType.project, id, ActivityService.LABELED,
                        ActivityDetail.names(assignment.added()));
            }
        }
        return created.withLabels(assigned);
    }

    /** The single-resource read: full canonical content plus labels. */
    public Project get(String idOrCode) {
        Project project = lookup.requireProject(idOrCode);
        return project.withLabels(labels.forEntity(EntityType.project, project.id()));
    }

    /** Lean list; without a status filter only active projects are visible. */
    public List<ProjectSummary> find(List<ProjectStatus> statuses, String label, String query, int limit) {
        List<ProjectStatus> effective = (statuses == null || statuses.isEmpty())
                ? ProjectStatus.defaultVisible()
                : statuses;
        return projects.find(effective, label, query, limit);
    }

    @Transactional
    public Project update(Caller caller, String idOrCode, Update command) {
        Project project = requireWritable(idOrCode);
        List<ActivityDetail.Change> changes = new ArrayList<>();

        String name = project.name();
        if (command.name() != null && !command.name().isBlank() && !command.name().strip().equals(project.name())) {
            name = command.name().strip();
            changes.add(new ActivityDetail.Change("name", name));
        }
        String html = project.descriptionHtml();
        String text = project.descriptionText();
        if (command.description() != null || command.descriptionHtml() != null) {
            RichText.Content content = RichText.canonicalize(command.description(), command.descriptionHtml());
            if (!content.html().equals(project.descriptionHtml())) {
                html = content.html();
                text = content.text();
                changes.add(new ActivityDetail.Change("description", null));
            }
        }

        boolean labelsGiven = command.labels() != null;
        if (changes.isEmpty() && !labelsGiven) {
            throw new IllegalArgumentException("nothing to change: pass at least one of name, description, "
                    + "description_html, labels");
        }
        if (!changes.isEmpty()) {
            projects.updateContent(project.id(), name, html, text);
            activities.record(caller, EntityType.project, project.id(), ActivityService.UPDATED,
                    ActivityDetail.changed(changes));
        }
        if (labelsGiven) {
            LabelService.Assignment assignment = labels.assign(EntityType.project, project.id(), command.labels());
            recordLabelChanges(caller, project.id(), assignment);
        }
        return get(project.shortCode());
    }

    /** One of the explicit lifecycle moves; anything not allowed by the model is a 409. */
    @Transactional
    public Project transition(Caller caller, String idOrCode, ProjectStatus target) {
        Project project = lookup.requireProject(idOrCode);
        if (project.status() == ProjectStatus.archived) {
            throw new ConflictException("project " + project.shortCode()
                    + " is archived: no transitions leave the archived state");
        }
        if (project.status() == target) {
            throw new ConflictException("project " + project.shortCode() + " is already " + target.value());
        }
        if (!project.status().allowedTransitions().contains(target)) {
            throw new ConflictException("project " + project.shortCode() + ": "
                    + ActivityDetail.transition(project.status().value(), target.value()) + " is not allowed");
        }
        projects.updateStatus(project.id(), target);
        activities.record(caller, EntityType.project, project.id(), ActivityService.STATUS_CHANGED,
                ActivityDetail.transition(project.status().value(), target.value()));
        return get(project.shortCode());
    }

    /** Resolves a project for a read path. */
    public Project require(String idOrCode) {
        return lookup.requireProject(idOrCode);
    }

    /** Resolves a project and rejects every write if it is archived. */
    public Project requireWritable(String idOrCode) {
        Project project = lookup.requireProject(idOrCode);
        requireNotArchived(project);
        return project;
    }

    /** Guards writes reached through another entity (its parent project is archived). */
    public void requireWritable(UUID projectId) {
        requireNotArchived(projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("no project " + projectId)));
    }

    /** Takes the next reference number from the project's shared counter. */
    public int nextSeq(UUID projectId) {
        return projects.nextSeq(projectId);
    }

    private void requireNotArchived(Project project) {
        if (project.status() == ProjectStatus.archived) {
            throw new ConflictException("project " + project.shortCode() + " is archived: the whole project "
                    + "is read-only, writes are rejected");
        }
    }

    private void recordLabelChanges(Caller caller, UUID projectId, LabelService.Assignment assignment) {
        if (!assignment.added().isEmpty()) {
            activities.record(caller, EntityType.project, projectId, ActivityService.LABELED,
                    ActivityDetail.names(assignment.added()));
        }
        if (!assignment.removed().isEmpty()) {
            activities.record(caller, EntityType.project, projectId, ActivityService.UNLABELED,
                    ActivityDetail.names(assignment.removed()));
        }
    }

    /**
     * A supplied code is validated; an absent one is DERIVED from the name. A derived
     * candidate shorter than two characters cannot be unique enough, so the caller has
     * to name the code explicitly.
     */
    private String resolveShortCode(String name, String requestedCode) {
        String shortCode;
        if (requestedCode == null || requestedCode.isBlank()) {
            String derived = ShortCodes.derive(name);
            if (derived.length() < 2) {
                throw new IllegalArgumentException("short_code is required for this name: '"
                        + name + "' yields fewer than 2 usable characters");
            }
            shortCode = derived;
        } else {
            shortCode = ShortCodes.normalize(requestedCode);
        }
        if (projects.existsByShortCode(shortCode)) {
            throw new ShortCodeConflictException(shortCode,
                    ShortCodes.suggestions(shortCode, projects::existsByShortCode));
        }
        return shortCode;
    }
}
