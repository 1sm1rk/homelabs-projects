package de.homelabs.projects.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.homelabs.projects.persistence.WorkPackageRepository;

/**
 * Work packages: always inside one project, numbered from the project's shared
 * counter, freely movable between statuses, labelable, assignable.
 *
 * <p>Every write asks the project guard first: a work package of an archived project
 * is read-only (409), including its labels and its assignee.
 */
@Service
public class WorkPackageService {

    private final WorkPackageRepository workPackages;
    private final ProjectService projects;
    private final EntityLookup lookup;
    private final LabelService labels;
    private final ActivityService activities;
    private final UserDirectory users;

    public WorkPackageService(WorkPackageRepository workPackages, ProjectService projects, EntityLookup lookup,
            LabelService labels, ActivityService activities, UserDirectory users) {
        this.workPackages = workPackages;
        this.projects = projects;
        this.lookup = lookup;
        this.labels = labels;
        this.activities = activities;
        this.users = users;
    }

    /** Input of {@code POST /api/projects/{idOrCode}/work-packages}. */
    public record Create(String title, String description, String descriptionHtml, String assigneeSub,
            String dueDate, List<String> labels) {
    }

    /** Input of {@code PATCH /api/work-packages/{idOrRef}}; {@code null} means "unchanged". */
    public record Update(String title, String description, String descriptionHtml, ItemStatus status,
            String assigneeSub, String dueDate, List<String> labels) {
    }

    /** Input of {@code GET /api/work-packages}. */
    public record Filter(String project, List<ItemStatus> statuses, String assignee, String query,
            boolean includeInactiveProjects, int limit) {
    }

    @Transactional
    public WorkPackage create(Caller caller, String projectRef, Create command) {
        Project project = projects.requireWritable(projectRef);
        RichText.Content content = RichText.canonicalize(command.description(), command.descriptionHtml());

        UUID id = UUID.randomUUID();
        int seq = projects.nextSeq(project.id());
        workPackages.insert(id, project.id(), seq, command.title().strip(), content.html(), content.text(),
                ItemStatus.todo, Values.trimToNull(command.assigneeSub()),
                Values.parseDate(command.dueDate(), "due_date"));
        activities.record(caller, EntityType.work_package, id, ActivityService.CREATED, null);
        assignLabels(caller, id, command.labels());
        return get(id.toString());
    }

    public WorkPackage get(String idOrRef) {
        WorkPackage item = lookup.requireWorkPackage(idOrRef);
        return item.withLabels(labels.forEntity(EntityType.work_package, item.id()))
                .withAssigneeName(users.name(item.assigneeSub()));
    }

    public List<ItemSummary> find(Filter filter, Caller caller) {
        List<ItemSummary> found = workPackages.find(filter.project(), filter.statuses(),
                caller.assigneeSub(filter.assignee()), filter.query(), filter.includeInactiveProjects(),
                filter.limit());
        Map<String, String> names = users.names(found.stream().map(ItemSummary::assigneeSub)
                .filter(Objects::nonNull).toList());
        return found.stream()
                .map(item -> item.assigneeSub() == null ? item
                        : item.withAssigneeName(names.getOrDefault(item.assigneeSub(), item.assigneeSub())))
                .toList();
    }

    @Transactional
    public WorkPackage update(Caller caller, String idOrRef, Update command) {
        WorkPackage item = lookup.requireWorkPackage(idOrRef);
        projects.requireWritable(item.projectId());
        List<ActivityDetail.Change> changes = new ArrayList<>();

        String title = item.title();
        if (command.title() != null) {
            if (command.title().isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            if (!command.title().strip().equals(item.title())) {
                title = command.title().strip();
                changes.add(new ActivityDetail.Change("title", title));
            }
        }
        String html = item.descriptionHtml();
        String text = item.descriptionText();
        if (command.description() != null || command.descriptionHtml() != null) {
            RichText.Content content = RichText.canonicalize(command.description(), command.descriptionHtml());
            if (!content.html().equals(item.descriptionHtml())) {
                html = content.html();
                text = content.text();
                changes.add(new ActivityDetail.Change("description", null));
            }
        }
        String assignee = item.assigneeSub();
        if (command.assigneeSub() != null) {
            String next = Values.trimToNull(command.assigneeSub());
            if (!Objects.equals(next, item.assigneeSub())) {
                assignee = next;
                changes.add(new ActivityDetail.Change("assignee_sub", next == null ? "none" : next));
            }
        }
        LocalDate dueDate = item.dueDate();
        if (command.dueDate() != null) {
            LocalDate next = Values.parseDate(command.dueDate(), "due_date");
            if (!Objects.equals(next, item.dueDate())) {
                dueDate = next;
                changes.add(new ActivityDetail.Change("due_date", next == null ? "none" : next.toString()));
            }
        }
        boolean statusChanged = command.status() != null && command.status() != item.status();
        boolean labelsGiven = command.labels() != null;
        if (changes.isEmpty() && !statusChanged && !labelsGiven) {
            throw new IllegalArgumentException("nothing to change: pass at least one of title, description, "
                    + "description_html, status, assignee_sub, due_date, labels");
        }
        if (!changes.isEmpty() || statusChanged) {
            ItemStatus status = statusChanged ? command.status() : item.status();
            workPackages.update(item.id(), title, html, text, status, assignee, dueDate);
            if (!changes.isEmpty()) {
                activities.record(caller, EntityType.work_package, item.id(), ActivityService.UPDATED,
                        ActivityDetail.changed(changes));
            }
            if (statusChanged) {
                activities.record(caller, EntityType.work_package, item.id(), ActivityService.STATUS_CHANGED,
                        ActivityDetail.transition(item.status().value(), status.value()));
            }
        }
        if (labelsGiven) {
            assignLabels(caller, item.id(), command.labels());
        }
        return get(item.id().toString());
    }

    private void assignLabels(Caller caller, UUID id, List<String> names) {
        if (names == null) {
            return;
        }
        LabelService.Assignment assignment = labels.assign(EntityType.work_package, id, names);
        if (!assignment.added().isEmpty()) {
            activities.record(caller, EntityType.work_package, id, ActivityService.LABELED,
                    ActivityDetail.names(assignment.added()));
        }
        if (!assignment.removed().isEmpty()) {
            activities.record(caller, EntityType.work_package, id, ActivityService.UNLABELED,
                    ActivityDetail.names(assignment.removed()));
        }
    }
}
