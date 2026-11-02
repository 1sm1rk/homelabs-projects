package de.homelabs.projects;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one-time backfill of {@code sort_order} over data that predates the column.
 *
 * <p>No Spring context here: the test drives Flyway itself, so the two halves of the
 * migration stay separable — migrate to the baseline, write rows with hand-chosen
 * {@code created_at} values (the API always writes "now", which would make creation order
 * and insertion order the same thing), then migrate the rest and look at the numbers.
 *
 * <p>The assertions are per project AND per table: tasks number 1..n, work packages 1..m,
 * both independently. The identical-timestamp pair proves the id tiebreak, and the
 * information_schema check proves the closing {@code SET NOT NULL}.
 */
@Testcontainers
class SortOrderBackfillIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static final UUID P1 = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID P2 = UUID.fromString("10000000-0000-0000-0000-000000000002");

    // Ids double as the expected order: …0001 before …0002 (the ids are the tiebreak).
    private static final UUID P1_WP_FIRST = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID P1_WP_SECOND = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID P1_WP_THIRD = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID P1_WP_TIE_A = UUID.fromString("20000000-0000-0000-0000-000000000004");
    private static final UUID P1_WP_TIE_B = UUID.fromString("20000000-0000-0000-0000-000000000005");
    private static final UUID P2_WP_FIRST = UUID.fromString("20000000-0000-0000-0000-000000000011");
    private static final UUID P2_WP_SECOND = UUID.fromString("20000000-0000-0000-0000-000000000012");

    private static final UUID P1_TASK_FIRST = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID P1_TASK_SECOND = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID P1_TASK_THIRD = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID P2_TASK_ONLY = UUID.fromString("30000000-0000-0000-0000-000000000011");

    @Test
    void backfillsExistingRowsInCreationOrderPerProjectAndTable() throws Exception {
        migrateTo("1");
        insertPreMigrationRows();

        migrateTo(null);

        Map<UUID, Integer> workPackages = sortOrders("work_package");
        Map<UUID, Integer> tasks = sortOrders("task");

        // creation order wins over insertion order: the earliest timestamp was inserted second
        assertThat(workPackages).containsEntry(P1_WP_FIRST, 1)
                .containsEntry(P1_WP_SECOND, 2)
                .containsEntry(P1_WP_THIRD, 3)
                // identical created_at: the id decides (…0004 before …0005)
                .containsEntry(P1_WP_TIE_A, 4)
                .containsEntry(P1_WP_TIE_B, 5);

        // the task sequence is its own: it starts at 1 although five work packages exist
        assertThat(tasks).containsEntry(P1_TASK_FIRST, 1)
                .containsEntry(P1_TASK_SECOND, 2)
                .containsEntry(P1_TASK_THIRD, 3);

        // per project, not global: the second project starts at 1 again, per table
        assertThat(workPackages).containsEntry(P2_WP_FIRST, 1).containsEntry(P2_WP_SECOND, 2);
        assertThat(tasks).containsEntry(P2_TASK_ONLY, 1);

        assertThat(nullability()).containsEntry("work_package.sort_order", "NO")
                .containsEntry("task.sort_order", "NO");
    }

    /** Runs the migrations up to {@code version}; {@code null} means "everything". */
    private void migrateTo(String version) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (version != null) {
            configuration = configuration.target(MigrationVersion.fromVersion(version));
        }
        configuration.load().migrate();
    }

    private void insertPreMigrationRows() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO project (id, short_code, name, owner_sub, owner_name) VALUES
                      ('%s', 'P1', 'Projekt eins', 'sub', 'owner'),
                      ('%s', 'P2', 'Projekt zwei', 'sub', 'owner')"""
                    .formatted(P1, P2));

            statement.execute("""
                    INSERT INTO work_package (id, project_id, title, seq, created_at) VALUES
                      ('%s', '%s', 'zweite', 2, '2026-10-01T09:00:00Z'),
                      ('%s', '%s', 'erste', 1, '2026-10-01T08:00:00Z'),
                      ('%s', '%s', 'dritte', 3, '2026-10-01T10:00:00Z'),
                      ('%s', '%s', 'gleich A', 4, '2026-10-01T11:00:00Z'),
                      ('%s', '%s', 'gleich B', 5, '2026-10-01T11:00:00Z'),
                      ('%s', '%s', 'zweite', 2, '2026-10-02T09:00:00Z'),
                      ('%s', '%s', 'erste', 1, '2026-10-02T08:00:00Z')"""
                    .formatted(P1_WP_SECOND, P1, P1_WP_FIRST, P1, P1_WP_THIRD, P1, P1_WP_TIE_A, P1, P1_WP_TIE_B,
                            P1, P2_WP_SECOND, P2, P2_WP_FIRST, P2));

            statement.execute("""
                    INSERT INTO task (id, project_id, title, seq, created_at) VALUES
                      ('%s', '%s', 'zweite', 6, '2026-10-01T09:30:00Z'),
                      ('%s', '%s', 'erste', 7, '2026-10-01T08:30:00Z'),
                      ('%s', '%s', 'dritte', 8, '2026-10-01T10:30:00Z'),
                      ('%s', '%s', 'einzige', 3, '2026-10-02T08:30:00Z')"""
                    .formatted(P1_TASK_SECOND, P1, P1_TASK_FIRST, P1, P1_TASK_THIRD, P1, P2_TASK_ONLY, P2));
        }
    }

    private Map<UUID, Integer> sortOrders(String table) throws SQLException {
        Map<UUID, Integer> orders = new LinkedHashMap<>();
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, sort_order FROM " + table)) {
            while (rows.next()) {
                orders.put(rows.getObject("id", UUID.class), rows.getInt("sort_order"));
            }
        }
        return orders;
    }

    private Map<String, String> nullability() throws SQLException {
        Map<String, String> nullability = new LinkedHashMap<>();
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT table_name || '.' || column_name AS column_name, is_nullable
                        FROM information_schema.columns
                        WHERE column_name = 'sort_order' ORDER BY table_name""")) {
            while (rows.next()) {
                nullability.put(rows.getString("column_name"), rows.getString("is_nullable"));
            }
        }
        return nullability;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
