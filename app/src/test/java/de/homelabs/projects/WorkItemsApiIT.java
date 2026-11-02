package de.homelabs.projects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Work packages and tasks over the real REST surface: the shared per-project counter,
 * reference resolution per endpoint, free status moves with their activity entries,
 * the filters (project, status, assignee/me, work package, due date, free text), lean
 * list answers and the regroup guard.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestSecurityConfig.class)
class WorkItemsApiIT extends ApiTestSupport {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void numbersWorkPackagesAndTasksFromOneSharedCounter() throws Exception {
        String token = bearerToken("it-item-sub");
        post("/api/projects", """
                {"name":"Arbeitspakete","short_code":"ARB"}""", token);

        Response pack = post("/api/projects/ARB/work-packages", """
                {"title":"Paket eins"}""", token);
        assertThat(pack.status()).isEqualTo(201);
        assertThat(pack.body())
                .contains("\"reference\":\"ARB-1\"")
                .contains("\"status\":\"todo\"")
                .contains("\"project_short_code\":\"ARB\"");

        Response loose = post("/api/tasks", """
                {"project":"ARB","title":"Aufgabe zwei"}""", token);
        assertThat(loose.status()).isEqualTo(201);
        assertThat(loose.body()).contains("\"reference\":\"ARB-2\"");

        Response grouped = post("/api/tasks", """
                {"project":"arb","work_package_id":"ARB-1","title":"Aufgabe drei"}""", token);
        assertThat(grouped.status()).isEqualTo(201);
        assertThat(grouped.body())
                .contains("\"reference\":\"ARB-3\"")
                .contains("\"work_package_reference\":\"ARB-1\"");
    }

    @Test
    void resolvesReferencesPerEndpointType() throws Exception {
        String token = bearerToken("it-ref-sub");
        post("/api/projects", """
                {"name":"Referenzen","short_code":"REF"}""", token);
        post("/api/projects/REF/work-packages", """
                {"title":"Paket"}""", token);
        post("/api/tasks", """
                {"project":"REF","title":"Aufgabe"}""", token);

        assertThat(get("/api/work-packages/REF-1", token).status()).isEqualTo(200);
        assertThat(get("/api/tasks/REF-2", token).status()).isEqualTo(200);

        // the reference of a task is not a work package and vice versa
        Response wrongType = get("/api/work-packages/REF-2", token);
        assertThat(wrongType.status()).isEqualTo(404);
        assertThat(wrongType.body()).contains("no work package REF-2");

        assertThat(get("/api/tasks/ZZZ-9", token).status()).isEqualTo(404);
    }

    @Test
    void movesStatusFreelyAndRecordsTheTransition() throws Exception {
        String token = bearerToken("it-status-sub");
        post("/api/projects", """
                {"name":"Statuswechsel","short_code":"MOVE"}""", token);
        post("/api/projects/MOVE/work-packages", """
                {"title":"Wanderndes Paket"}""", token);

        Response patched = patch("/api/work-packages/MOVE-1", """
                {"status":"in_progress"}""", token);
        assertThat(patched.status()).isEqualTo(200);
        assertThat(patched.body()).contains("\"status\":\"in_progress\"");

        Response activity = get("/api/activities?parent=work_package:MOVE-1", token);
        assertThat(activity.body())
                .contains("\"event_type\":\"status_changed\"")
                .contains("todo → in_progress");

        // done and cancelled stay editable — only an archived project is a hard stop
        assertThat(patch("/api/work-packages/MOVE-1", """
                {"status":"done","title":"Wanderndes Paket (fertig)"}""", token).status()).isEqualTo(200);
    }

    @Test
    void filtersByProjectStatusAssigneeWorkPackageAndDueDate() throws Exception {
        String token = bearerToken("it-filter-sub");
        post("/api/projects", """
                {"name":"Filterliste","short_code":"FILT"}""", token);
        post("/api/projects/FILT/work-packages", """
                {"title":"Filterpaket"}""", token);

        post("/api/tasks", """
                {"project":"FILT","work_package_id":"FILT-1","title":"Aufgabe früh",
                 "assignee_sub":"it-filter-sub","due_date":"2026-11-01"}""", token);
        post("/api/tasks", """
                {"project":"FILT","title":"Aufgabe spät","assignee_sub":"it-other-sub",
                 "due_date":"2026-12-01"}""", token);
        // items are created in 'todo'; statuses move through the update path only
        assertThat(patch("/api/tasks/FILT-2", """
                {"status":"blocked"}""", token).status()).isEqualTo(200);

        Response byProject = get("/api/tasks?project=filt", token);
        assertThat(byProject.body()).contains("FILT-2").contains("FILT-3");

        Response byStatus = get("/api/tasks?project=FILT&status=blocked", token);
        assertThat(byStatus.body()).contains("\"reference\":\"FILT-2\"").doesNotContain("\"reference\":\"FILT-3\"");

        Response byMe = get("/api/tasks?project=FILT&assignee=me", token);
        assertThat(byMe.body()).contains("\"reference\":\"FILT-2\"").doesNotContain("\"reference\":\"FILT-3\"");

        Response byOther = get("/api/tasks?project=FILT&assignee=it-other-sub", token);
        assertThat(byOther.body()).contains("\"reference\":\"FILT-3\"").doesNotContain("\"reference\":\"FILT-2\"");
        assertThat(byOther.body()).contains("\"assignee_name\":\"it-other-sub\"");

        Response byWorkPackage = get("/api/tasks?wp=FILT-1", token);
        assertThat(byWorkPackage.body()).contains("\"reference\":\"FILT-2\"").doesNotContain("\"reference\":\"FILT-3\"");

        Response dueBefore = get("/api/tasks?project=FILT&due_before=2026-11-15", token);
        assertThat(dueBefore.body()).contains("\"reference\":\"FILT-2\"").doesNotContain("\"reference\":\"FILT-3\"");
    }

    @Test
    void listAnswersAreLean() throws Exception {
        String token = bearerToken("it-lean-sub");
        post("/api/projects", """
                {"name":"Schlanke Listen","short_code":"LEAN"}""", token);
        post("/api/projects/LEAN/work-packages", """
                {"title":"Paket mit Inhalt","description":"Ein etwas längerer Beschreibungstext für den Auszug."}""",
                token);

        Response list = get("/api/work-packages?project=LEAN", token);

        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains("\"excerpt\":").contains("\"reference\":\"LEAN-1\"");
        assertThat(list.body()).doesNotContain("description_html").doesNotContain("description_text");
    }

    @Test
    void regroupKeepsTasksInsideTheirOwnProject() throws Exception {
        String token = bearerToken("it-regroup-sub");
        post("/api/projects", """
                {"name":"Gruppe A","short_code":"GRPA"}""", token);
        post("/api/projects", """
                {"name":"Gruppe B","short_code":"GRPB"}""", token);
        post("/api/projects/GRPA/work-packages", """
                {"title":"Paket A"}""", token);
        post("/api/projects/GRPB/work-packages", """
                {"title":"Paket B"}""", token);
        post("/api/tasks", """
                {"project":"GRPA","title":"Wandernde Aufgabe"}""", token);

        Response foreignGroup = patch("/api/tasks/GRPA-2", """
                {"work_package_id":"GRPB-1"}""", token);
        assertThat(foreignGroup.status()).isEqualTo(400);
        assertThat(foreignGroup.body()).contains("own project");

        Response regrouped = patch("/api/tasks/GRPA-2", """
                {"work_package_id":"GRPA-1"}""", token);
        assertThat(regrouped.status()).isEqualTo(200);
        assertThat(regrouped.body()).contains("\"work_package_reference\":\"GRPA-1\"");
    }

    @Test
    void clearsAssigneeAndDueDateWithEmptyStrings() throws Exception {
        String token = bearerToken("it-clear-sub");
        post("/api/projects", """
                {"name":"Leeren","short_code":"CLEAR"}""", token);
        post("/api/projects/CLEAR/work-packages", """
                {"title":"Mit Zuordnung","assignee_sub":"it-clear-sub","due_date":"2026-11-01"}""", token);

        Response cleared = patch("/api/work-packages/CLEAR-1", """
                {"assignee_sub":"","due_date":""}""", token);

        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.body()).contains("\"assignee_sub\":null").contains("\"due_date\":null");
    }

    /**
     * Creates assign the position server-side: max + 1 inside the project, and the
     * task and work-package sequences count independently (both start at 1). A second
     * project starts its own sequences at 1.
     */
    @Test
    void assignsSortOrderPerTableInsideEachProject() throws Exception {
        String token = bearerToken("it-sort-create-sub");
        post("/api/projects", """
                {"name":"Reihenfolge","short_code":"ORD"}""", token);
        post("/api/projects", """
                {"name":"Reihenfolge zwei","short_code":"ORD2"}""", token);

        Response firstPack = post("/api/projects/ORD/work-packages", """
                {"title":"Paket eins"}""", token);
        Response secondPack = post("/api/projects/ORD2/work-packages", """
                {"title":"Paket zwei"}""", token);
        Response firstTask = post("/api/tasks", """
                {"project":"ORD","title":"Aufgabe eins"}""", token);
        Response secondTask = post("/api/tasks", """
                {"project":"ORD","title":"Aufgabe zwei"}""", token);

        assertThat(firstPack.body()).contains("\"sort_order\":1");
        assertThat(secondPack.body()).contains("\"sort_order\":1");
        assertThat(firstTask.body()).contains("\"sort_order\":1");
        assertThat(secondTask.body()).contains("\"sort_order\":2");

        // the second work package of the FIRST project continues at 2
        assertThat(post("/api/projects/ORD/work-packages", """
                {"title":"Paket drei"}""", token).body()).contains("\"sort_order\":2");

        // the lean summaries carry the field too
        assertThat(get("/api/tasks?project=ORD", token).body())
                .contains("\"sort_order\":1")
                .contains("\"sort_order\":2");
        assertThat(get("/api/work-packages?project=ORD", token).body()).contains("\"sort_order\":2");
    }

    /**
     * An explicit {@code sort_order} wins over creation order in every list — for tasks
     * and work packages alike. The tiebreak (created_at, then id) is only the fallback.
     */
    @Test
    void explicitSortOrderWinsOverCreationOrderInLists() throws Exception {
        String token = bearerToken("it-sort-order-sub");
        post("/api/projects", """
                {"name":"Sortierte Listen","short_code":"SORT"}""", token);
        post("/api/projects/SORT/work-packages", """
                {"title":"Paket A"}""", token);
        post("/api/projects/SORT/work-packages", """
                {"title":"Paket B"}""", token);
        post("/api/projects/SORT/work-packages", """
                {"title":"Paket C"}""", token);
        post("/api/tasks", """
                {"project":"SORT","title":"Aufgabe A"}""", token);
        post("/api/tasks", """
                {"project":"SORT","title":"Aufgabe B"}""", token);
        post("/api/tasks", """
                {"project":"SORT","title":"Aufgabe C"}""", token);

        // SORT-1..3 are the work packages, SORT-4..6 the tasks (one shared counter).
        // The resulting order is B, C, A — deliberately neither the creation order
        // (A, B, C) nor its reverse, so only sort_order can produce it.
        assertThat(patch("/api/work-packages/SORT-2", """
                {"sort_order":1}""", token).status()).isEqualTo(200);
        assertThat(patch("/api/work-packages/SORT-3", """
                {"sort_order":2}""", token).status()).isEqualTo(200);
        assertThat(patch("/api/work-packages/SORT-1", """
                {"sort_order":3}""", token).status()).isEqualTo(200);
        assertThat(patch("/api/tasks/SORT-5", """
                {"sort_order":1}""", token).status()).isEqualTo(200);
        assertThat(patch("/api/tasks/SORT-6", """
                {"sort_order":2}""", token).status()).isEqualTo(200);
        assertThat(patch("/api/tasks/SORT-4", """
                {"sort_order":3}""", token).status()).isEqualTo(200);

        // the lists read the explicit positions, not the creation order
        String packs = get("/api/work-packages?project=SORT", token).body();
        assertThat(indexOf(packs, "SORT-2")).isLessThan(indexOf(packs, "SORT-3"));
        assertThat(indexOf(packs, "SORT-3")).isLessThan(indexOf(packs, "SORT-1"));

        String tasks = get("/api/tasks?project=SORT", token).body();
        assertThat(indexOf(tasks, "SORT-5")).isLessThan(indexOf(tasks, "SORT-6"));
        assertThat(indexOf(tasks, "SORT-6")).isLessThan(indexOf(tasks, "SORT-4"));
    }

    /** The tri-state of {@code sort_order}: a value sets it, null leaves it alone, below 1 is rejected. */
    @Test
    void patchSortOrderSetsRejectsBelowOneAndKeepsItOnNull() throws Exception {
        String token = bearerToken("it-sort-patch-sub");
        post("/api/projects", """
                {"name":"Reihenfolge patchen","short_code":"PATCH"}""", token);
        post("/api/projects/PATCH/work-packages", """
                {"title":"Bewegliches Paket"}""", token);
        post("/api/tasks", """
                {"project":"PATCH","title":"Bewegliche Aufgabe"}""", token);

        Response moved = patch("/api/tasks/PATCH-2", """
                {"sort_order":7}""", token);
        assertThat(moved.status()).isEqualTo(200);
        assertThat(moved.body()).contains("\"sort_order\":7");

        // null means unchanged: another field moves, the position stays
        Response retitled = patch("/api/tasks/PATCH-2", """
                {"title":"Bewegliche Aufgabe (neu)"}""", token);
        assertThat(retitled.body()).contains("\"sort_order\":7");
        assertThat(get("/api/tasks/PATCH-2", token).body()).contains("\"sort_order\":7");

        Response movedPack = patch("/api/work-packages/PATCH-1", """
                {"sort_order":3}""", token);
        assertThat(movedPack.status()).isEqualTo(200);
        assertThat(movedPack.body()).contains("\"sort_order\":3");

        Response zero = patch("/api/tasks/PATCH-2", """
                {"sort_order":0}""", token);
        assertThat(zero.status()).isEqualTo(400);
        assertThat(zero.body()).contains("sort_order");

        Response negative = patch("/api/work-packages/PATCH-1", """
                {"sort_order":-2}""", token);
        assertThat(negative.status()).isEqualTo(400);
        assertThat(negative.body()).contains("sort_order");

        // a repeated value is no change at all
        Response same = patch("/api/tasks/PATCH-2", """
                {"sort_order":7}""", token);
        assertThat(same.status()).isEqualTo(400);
        assertThat(same.body()).contains("nothing to change");

        // the position is first-class: it shows up in the activity log
        assertThat(get("/api/activities?parent=task:PATCH-2", token).body()).contains("sort_order='7'");
    }

    @Test
    void rejectsEmptyPatchesAndInvalidDates() throws Exception {
        String token = bearerToken("it-err-sub");
        post("/api/projects", """
                {"name":"Fehlerfälle","short_code":"ERR"}""", token);
        post("/api/projects/ERR/work-packages", """
                {"title":"Fehlerpaket"}""", token);

        Response nothing = patch("/api/work-packages/ERR-1", """
                {"title":"Fehlerpaket"}""", token);
        assertThat(nothing.status()).isEqualTo(400);
        assertThat(nothing.body()).contains("nothing to change");

        Response badDate = patch("/api/work-packages/ERR-1", """
                {"due_date":"01.11.2026"}""", token);
        assertThat(badDate.status()).isEqualTo(400);
        assertThat(badDate.body()).contains("ISO date");

        Response badStatus = patch("/api/work-packages/ERR-1", """
                {"status":"gibberish"}""", token);
        assertThat(badStatus.status()).isEqualTo(400);

        Response unknownProject = post("/api/projects/ZZZZ/work-packages", """
                {"title":"Nirgendwo"}""", token);
        assertThat(unknownProject.status()).isEqualTo(404);
    }

    /** The offset of a reference inside a JSON array body, for order assertions. */
    private static int indexOf(String body, String reference) {
        int index = body.indexOf("\"reference\":\"" + reference + "\"");
        assertThat(index).as("reference %s in %s", reference, body).isNotNegative();
        return index;
    }
}
