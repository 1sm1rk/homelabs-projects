package de.homelabs.projects.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.homelabs.projects.domain.Activity;
import de.homelabs.projects.domain.ActivityService;
import de.homelabs.projects.domain.EntityLookup;

/**
 * The append-only activity log of one entity, newest first. One row per mutation:
 * created, updated, status_changed, labeled, unlabeled, commented, attached, detached.
 */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activities;
    private final EntityLookup lookup;

    public ActivityController(ActivityService activities, EntityLookup lookup) {
        this.activities = activities;
        this.lookup = lookup;
    }

    @GetMapping
    public List<Activity> list(@RequestParam String parent, @RequestParam(required = false) Integer limit) {
        EntityLookup.ResolvedParent resolved = lookup.requireParent(parent);
        return activities.list(resolved.type(), resolved.id(),
                ApiSupport.clamp(limit, ApiSupport.DEFAULT_ACTIVITY_LIMIT, ApiSupport.MAX_ACTIVITY_LIMIT));
    }
}
