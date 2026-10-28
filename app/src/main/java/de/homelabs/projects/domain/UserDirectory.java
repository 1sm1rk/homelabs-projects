package de.homelabs.projects.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import de.homelabs.projects.persistence.AppUserRepository;

/**
 * Display names of the people using the service. {@code app_user} is fed from the
 * JWT on every authenticated request (see the servlet filter), so the local table is
 * always at least as fresh as the last call of that principal — and no Keycloak
 * admin-API dependency is needed for a name.
 *
 * <p>Unknown subjects (an assignee who never called the API) fall back to the subject
 * itself instead of an empty name.
 */
@Service
public class UserDirectory {

    private final AppUserRepository users;

    public UserDirectory(AppUserRepository users) {
        this.users = users;
    }

    /** Records that this principal was seen now. */
    public void touch(String sub, String username) {
        if (sub != null && !sub.isBlank()) {
            users.touch(sub, username == null || username.isBlank() ? sub : username);
        }
    }

    /** Display name of one subject; {@code null} in, {@code null} out. */
    public String name(String sub) {
        if (sub == null || sub.isBlank()) {
            return null;
        }
        return users.displayNames(List.of(sub)).getOrDefault(sub, sub);
    }

    /** Display names of several subjects, with the sub as fallback. */
    public Map<String, String> names(Collection<String> subs) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (subs == null) {
            return resolved;
        }
        Map<String, String> known = users.displayNames(subs);
        for (String sub : subs) {
            if (sub != null && !sub.isBlank()) {
                resolved.put(sub, known.getOrDefault(sub, sub));
            }
        }
        return resolved;
    }
}
