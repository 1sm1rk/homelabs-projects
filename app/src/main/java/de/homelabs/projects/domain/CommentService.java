package de.homelabs.projects.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.homelabs.projects.persistence.CommentRepository;

/**
 * Comments: plain text, on all three levels, create and list only in Stage 1.
 *
 * <p>Writing into an archived project's subtree is rejected like every other write
 * (409) — read-only means read-only.
 */
@Service
public class CommentService {

    private final CommentRepository comments;
    private final ProjectService projects;
    private final EntityLookup lookup;
    private final ActivityService activities;

    public CommentService(CommentRepository comments, ProjectService projects, EntityLookup lookup,
            ActivityService activities) {
        this.comments = comments;
        this.projects = projects;
        this.lookup = lookup;
        this.activities = activities;
    }

    @Transactional
    public Comment create(Caller caller, String parent, String text) {
        EntityLookup.ResolvedParent resolved = lookup.requireParent(parent);
        projects.requireWritable(resolved.projectId());

        UUID id = UUID.randomUUID();
        comments.insert(id, resolved.type(), resolved.id(), caller.sub(), caller.username(), text);
        activities.record(caller, resolved.type(), resolved.id(), ActivityService.COMMENTED, id.toString());
        return comments.findById(id).orElseThrow();
    }

    /** Newest first. */
    public List<Comment> list(String parent, int limit) {
        EntityLookup.ResolvedParent resolved = lookup.requireParent(parent);
        return comments.findForEntity(resolved.type(), resolved.id(), limit);
    }
}
