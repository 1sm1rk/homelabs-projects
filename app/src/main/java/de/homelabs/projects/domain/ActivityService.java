package de.homelabs.projects.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import de.homelabs.projects.persistence.ActivityRepository;

/**
 * The append-only activity log: one row per mutation, never edited, never deleted —
 * the trace agents (and later the UI) read to understand what happened before.
 *
 * <p>The event types are the fixed catalog of the domain concept; the detail strings
 * are composed by {@link ActivityDetail}.
 */
@Service
public class ActivityService {

    public static final String CREATED = "created";
    public static final String UPDATED = "updated";
    public static final String STATUS_CHANGED = "status_changed";
    public static final String LABELED = "labeled";
    public static final String UNLABELED = "unlabeled";
    public static final String COMMENTED = "commented";
    public static final String ATTACHED = "attached";
    public static final String DETACHED = "detached";

    private final ActivityRepository activities;

    public ActivityService(ActivityRepository activities) {
        this.activities = activities;
    }

    /** Appends one entry, stamped with the caller and the harness identity when known. */
    public void record(Caller caller, EntityType entityType, UUID entityId, String eventType, String detail) {
        activities.insert(new Activity(UUID.randomUUID(), entityType, entityId, caller.sub(), caller.username(),
                caller.source(), eventType, detail, null));
    }

    /** Newest first, as everything the agents read. */
    public List<Activity> list(EntityType entityType, UUID entityId, int limit) {
        return activities.findForEntity(entityType, entityId, limit);
    }
}
