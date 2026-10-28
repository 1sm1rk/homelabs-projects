package de.homelabs.projects.domain;

import java.util.List;

/**
 * Composes the compact {@code detail} strings of the activity log. One row per
 * mutation, never edited: the detail is the human- and agent-readable summary of
 * what changed.
 *
 * <ul>
 *   <li>{@code updated} — changed field names, scalar values inline:
 *       {@code title='New title', due_date=2026-11-01, description}</li>
 *   <li>{@code status_changed} — {@code active → deactivated}</li>
 *   <li>{@code labeled} / {@code unlabeled} — the label names</li>
 * </ul>
 */
public final class ActivityDetail {

    /** One changed field; a {@code null} value means the field name alone (long content). */
    public record Change(String field, String value) {
    }

    /** The {@code updated} detail; {@code null} when nothing changed. */
    public static String changed(List<Change> changes) {
        if (changes == null || changes.isEmpty()) {
            return null;
        }
        StringBuilder detail = new StringBuilder();
        for (Change change : changes) {
            if (!detail.isEmpty()) {
                detail.append(", ");
            }
            detail.append(change.field());
            if (change.value() != null) {
                detail.append("='").append(change.value()).append('\'');
            }
        }
        return detail.toString();
    }

    /** The {@code status_changed} detail, e.g. {@code active → deactivated}. */
    public static String transition(Object from, Object to) {
        return from + " → " + to;
    }

    /** Label names for {@code labeled} / {@code unlabeled}. */
    public static String names(List<String> names) {
        return names == null || names.isEmpty() ? null : String.join(", ", names);
    }

    private ActivityDetail() {
    }
}
