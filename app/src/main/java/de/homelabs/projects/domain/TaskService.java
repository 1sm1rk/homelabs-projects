package de.homelabs.projects.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.homelabs.projects.persistence.TaskRepository;

/**
 * Tasks: always inside one project, optionally grouped into one of that project's
 * work packages (regroupable), numbered from the same per-project counter as the work
 * packages — so every reference is unique within its project.
 *
 * <p>Every write asks the project guard first: a task of an archived project is
 * read-only (409).
 */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final ProjectService projects;
    private final EntityLookup lookup;
    private final LabelService labels;
    private final ActivityService activities;
    private final UserDirectory users;

    public TaskService(TaskRepository tasks, ProjectService projects, EntityLookup lookup, LabelService labels,
            ActivityService activities, UserDirectory users) {
        this.tasks = tasks;
        this.projects = projects;
        this.lookup = lookup;
        this.labels = labels;
        this.activities = activities;
        this.users = users;
    }

    /** Input of {@code POST /api/tasks}: the project comes from the body. */
    public record Create(String title, String description, String descriptionHtml, String project,
            String workPackageId, String assigneeSub, String dueDate, List<String> labels) {
    }

    /** Input of {@code PATCH /api/tasks/{idOrRef}}; {@code null} means "unchanged". */
    public record Update(String title, String description, String descriptionHtml, ItemStatus status,
            String assigneeSub, String dueDate, String workPackageId, List<String> labels) {
    }

    /** Input of {@code GET /api/tasks}. */
    public record Filter(String project, String workPackage, List<ItemStatus> statuses, String assignee,
            LocalDate dueBefore, String query, boolean includeInactiveProjects, int limit) {
    }

    @Transactional
    public Task create(Caller caller, Create command) {
        Project project = projects.requireWritable(command.project());
        UUID workPackageId = resolveWorkPackage(command.workPackageId(), project);
        RichText.Content content = RichText.canonicalize(command.description(), command.descriptionHtml());

        UUID id = UUID.randomUUID();
        int seq = projects.nextSeq(project.id());
        tasks.insert(id, project.id(), workPackageId, seq, command.title().strip(), content.html(), content.text(),
                ItemStatus.todo, Values.trimToNull(command.assigneeSub()),
                Values.parseDate(command.dueDate(), "due_date"));
        activities.record(caller, EntityType.task, id, ActivityService.CREATED, null);
        assignLabels(caller, id, command.labels());
        return get(id.toString());
    }

    public Task get(String idOrRef) {
        Task task = lookup.requireTask(idOrRef);
        return task.withLabels(labels.forEntity(EntityType.task, task.id()))
                .withAssigneeName(users.name(task.assigneeSub()));
    }

    public List<ItemSummary> find(Filter filter, Caller caller) {
        UUID workPackageId = filter.workPackage() == null || filter.workPackage().isBlank()
                ? null
                : lookup.requireWorkPackage(filter.workPackage()).id();
        List<ItemSummary> found = tasks.find(filter.project(), workPackageId, filter.statuses(),
                caller.assigneeSub(filter.assignee()), filter.dueBefore(), filter.query(),
                filter.includeInactiveProjects(), filter.limit());
        Map<String, String> names = users.names(found.stream().map(ItemSummary::assigneeSub)
                .filter(Objects::nonNull).toList());
        return found.stream()
                .map(item -> item.assigneeSub() == null ? item
                        : item.withAssigneeName(names.getOrDefault(item.assigneeSub(), item.assigneeSub())))
                .toList();
    }

    @Transactional
    public Task update(Caller caller, String idOrRef, Update command) {
        Task task = lookup.requireTask(idOrRef);
        projects.requireWritable(task.projectId());
        List<ActivityDetail.Change> changes = new ArrayList<>();

        String title = task.title();
        if (command.title() != null) {
            if (command.title().isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            if (!command.title().strip().equals(task.title())) {
                title = command.title().strip();
                changes.add(new ActivityDetail.Change("title", title));
            }
        }
        String html = task.descriptionHtml();
        String text = task.descriptionText();
        if (command.description() != null || command.descriptionHtml() != null) {
            RichText.Content content = RichText.canonicalize(command.description(), command.descriptionHtml());
            if (!content.html().equals(task.descriptionHtml())) {
                html = content.html();
                text = content.text();
                changes.add(new ActivityDetail.Change("description", null));
            }
        }
        String assignee = task.assigneeSub();
        if (command.assigneeSub() != null) {
            String next = Values.trimToNull(command.assigneeSub());
            if (!Objects.equals(next, task.assigneeSub())) {
                assignee = next;
                changes.add(new ActivityDetail.Change("assignee_sub", next == null ? "none" : next));
            }
        }
        LocalDate dueDate = task.dueDate();
        if (command.dueDate() != null) {
            LocalDate next = Values.parseDate(command.dueDate(), "due_date");
            if (!Objects.equals(next, task.dueDate())) {
                dueDate = next;
                changes.add(new ActivityDetail.Change("due_date", next == null ? "none" : next.toString()));
            }
        }
        UUID workPackageId = task.workPackageId();
        if (command.workPackageId() != null) {
            UUID next = resolveWorkPackage(command.workPackageId(), task.projectId(), task.projectShortCode());
            if (!Objects.equals(next, task.workPackageId())) {
                workPackageId = next;
                changes.add(new ActivityDetail.Change("work_package",
                        next == null ? "none" : lookup.requireWorkPackage(next.toString()).reference()));
            }
        }
        boolean statusChanged = command.status() != null && command.status() != task.status();
        boolean labelsGiven = command.labels() != null;
        if (changes.isEmpty() && !statusChanged && !labelsGiven) {
            throw new IllegalArgumentException("nothing to change: pass at least one of title, description, "
                    + "description_html, status, assignee_sub, due_date, work_package_id, labels");
        }
        if (!changes.isEmpty() || statusChanged) {
            ItemStatus status = statusChanged ? command.status() : task.status();
            tasks.update(task.id(), workPackageId, title, html, text, status, assignee, dueDate);
            if (!changes.isEmpty()) {
                activities.record(caller, EntityType.task, task.id(), ActivityService.UPDATED,
                        ActivityDetail.changed(changes));
            }
            if (statusChanged) {
                activities.record(caller, EntityType.task, task.id(), ActivityService.STATUS_CHANGED,
                        ActivityDetail.transition(task.status().value(), status.value()));
            }
        }
        if (labelsGiven) {
            assignLabels(caller, task.id(), command.labels());
        }
        return get(task.id().toString());
    }

    /** A task may only be grouped into a work package of its OWN project. */
    private UUID resolveWorkPackage(String reference, Project project) {
        return resolveWorkPackage(reference, project.id(), project.shortCode());
    }

    private UUID resolveWorkPackage(String reference, UUID projectId, String projectShortCode) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        WorkPackage workPackage = lookup.requireWorkPackage(reference);
        if (!workPackage.projectId().equals(projectId)) {
            throw new IllegalArgumentException("work_package " + workPackage.reference()
                    + " belongs to project " + workPackage.projectShortCode()
                    + " — a task can only join a work package of its own project " + projectShortCode);
        }
        return workPackage.id();
    }

    private void assignLabels(Caller caller, UUID id, List<String> names) {
        if (names == null) {
            return;
        }
        LabelService.Assignment assignment = labels.assign(EntityType.task, id, names);
        if (!assignment.added().isEmpty()) {
            activities.record(caller, EntityType.task, id, ActivityService.LABELED,
                    ActivityDetail.names(assignment.added()));
        }
        if (!assignment.removed().isEmpty()) {
            activities.record(caller, EntityType.task, id, ActivityService.UNLABELED,
                    ActivityDetail.names(assignment.removed()));
        }
    }
}
