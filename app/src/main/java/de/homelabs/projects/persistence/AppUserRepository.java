package de.homelabs.projects.persistence;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to the local user directory: {@code sub} plus the
 * {@code preferred_username} snapshot, upserted from the JWT on every authenticated
 * request. Display names are resolved from here, so the service needs no Keycloak
 * admin-API dependency.
 */
@Repository
public class AppUserRepository {

    private final JdbcClient jdbc;

    public AppUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Records that this principal was seen; the username snapshot follows the token. */
    public void touch(String sub, String username) {
        jdbc.sql("INSERT INTO app_user (sub, username) VALUES (?, ?) "
                        + "ON CONFLICT (sub) DO UPDATE SET username = excluded.username, last_seen_at = now()")
                .param(sub)
                .param(username)
                .update();
    }

    /** Display names for a set of subjects; subjects never seen are simply absent. */
    public Map<String, String> displayNames(Collection<String> subs) {
        Map<String, String> names = new LinkedHashMap<>();
        List<String> wanted = subs == null ? List.of()
                : subs.stream().filter(sub -> sub != null && !sub.isBlank()).distinct().toList();
        if (wanted.isEmpty()) {
            return names;
        }
        jdbc.sql("SELECT sub, username FROM app_user WHERE sub IN (:subs)")
                .param("subs", wanted)
                .query((rs, rowNum) -> Map.entry(rs.getString("sub"), rs.getString("username")))
                .list()
                .forEach(entry -> names.put(entry.getKey(), entry.getValue()));
        return names;
    }
}
