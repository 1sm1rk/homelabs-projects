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
}
