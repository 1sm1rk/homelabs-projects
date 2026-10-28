package de.homelabs.projects.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import de.homelabs.projects.persistence.LabelRepository;

/**
 * Labels: created on demand at the first assignment, names unique
 * case-insensitively and stored lowercased, ≤ 50 characters.
 *
 * <p>Assignment has FULL-REPLACEMENT semantics per write: the caller passes the
 * complete set it wants attached, never a delta. The returned {@link Assignment}
 * tells the calling service what was added and removed so the activity log can
 * record both.
 */
@Service
public class LabelService {

    private static final int MAX_NAME_LENGTH = 50;

    private final LabelRepository labels;

    public LabelService(LabelRepository labels) {
        this.labels = labels;
    }

    /** What a replacement changed, plus the resulting label set. */
    public record Assignment(List<String> added, List<String> removed, List<Label> labels) {
    }

    public List<Label> forEntity(EntityType type, UUID entityId) {
        return labels.forEntity(type, entityId);
    }

    public Map<UUID, List<Label>> forEntities(EntityType type, Collection<UUID> entityIds) {
        return labels.forEntities(type, entityIds);
    }

    /** Every known label, for discovery of the label filters. */
    public List<Label> all() {
        return labels.findAll();
    }

    /** Replaces the label set of one entity, creating unknown labels on the way. */
    public Assignment assign(EntityType type, UUID entityId, List<String> names) {
        List<String> wanted = normalize(names);
        List<Label> before = labels.forEntity(type, entityId);
        List<String> beforeNames = before.stream().map(Label::name).toList();

        List<UUID> labelIds = new ArrayList<>(wanted.size());
        for (String name : wanted) {
            Optional<Label> existing = labels.findByName(name);
            if (existing.isPresent()) {
                labelIds.add(existing.get().id());
            } else {
                UUID id = UUID.randomUUID();
                labels.insert(id, name);
                labelIds.add(id);
            }
        }
        labels.replace(type, entityId, labelIds);

        List<String> added = wanted.stream().filter(name -> !beforeNames.contains(name)).toList();
        List<String> removed = beforeNames.stream().filter(name -> !wanted.contains(name)).toList();
        return new Assignment(added, removed, labels.forEntity(type, entityId));
    }

    private static List<String> normalize(List<String> names) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String lower = name.trim().toLowerCase(Locale.ROOT);
            if (lower.length() > MAX_NAME_LENGTH) {
                throw new IllegalArgumentException(
                        "label names must be at most " + MAX_NAME_LENGTH + " characters, got '" + name + "'");
            }
            normalized.add(lower);
        }
        return List.copyOf(normalized);
    }
}
