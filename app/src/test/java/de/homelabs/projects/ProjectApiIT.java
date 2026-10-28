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
 * Project CRUD over the real REST surface: derived and explicit short codes, the
 * collision answer with suggestions, id/code resolution, partial updates, the content
 * pipeline and on-demand labels.
 *
 * <p>One container per class, shared across the methods — every assertion names a
 * unique marker or code, never a whole-collection match.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestSecurityConfig.class)
class ProjectApiIT extends ApiTestSupport {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void derivesTheShortCodeFromTheName() throws Exception {
        Response created = post("/api/projects", """
                {"name":"Homelabs Projects"}""", bearerToken("it-derive-sub"));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body()).contains("\"short_code\":\"HOMELA\"");
    }

    @Test
    void normalizesAnExplicitCodeAndReadsByCodeAndId() throws Exception {
        String token = bearerToken("it-read-sub");

        Response created = post("/api/projects", """
                {"name":"Handbuch Projekt","short_code":"hp","description":"ein Handbuch"}""", token);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body()).contains("\"short_code\":\"HP\"");

        Response byCode = get("/api/projects/HP", token);
        assertThat(byCode.status()).isEqualTo(200);
        assertThat(byCode.body()).contains("\"short_code\":\"HP\"").contains("\"description_html\":\"<p>ein Handbuch</p>\"");

        Response byId = get("/api/projects/" + uuidIn(created.body()), token);
        assertThat(byId.status()).isEqualTo(200);
        assertThat(byId.body()).contains("\"short_code\":\"HP\"");

        Response unknown = get("/api/projects/ZZZZ", token);
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.body()).contains("no project ZZZZ");
    }

    @Test
    void answersACollisionWithSuggestionsAndAcceptsTheRetry() throws Exception {
        String token = bearerToken("it-collision-sub");

        assertThat(post("/api/projects", """
                {"name":"Erster","short_code":"KOL"}""", token).status()).isEqualTo(201);

        Response collision = post("/api/projects", """
                {"name":"Zweiter","short_code":"KOL"}""", token);
        assertThat(collision.status()).isEqualTo(409);
        assertThat(collision.body()).contains("already taken").contains("\"suggestions\":[\"KOL2\"");

        Response retry = post("/api/projects", """
                {"name":"Zweiter","short_code":"KOL2"}""", token);
        assertThat(retry.status()).isEqualTo(201);
        assertThat(retry.body()).contains("\"short_code\":\"KOL2\"");
    }

    @Test
    void rejectsACodeFormatThatCannotBeUniqueEnough() throws Exception {
        Response tooShort = post("/api/projects", """
                {"name":"Kurz","short_code":"K"}""", bearerToken("it-format-sub"));

        assertThat(tooShort.status()).isEqualTo(400);
        assertThat(tooShort.body()).contains("short_code");
    }

    @Test
    void patchesNameAndPlainDescriptionIntoCanonicalHtml() throws Exception {
        String token = bearerToken("it-patch-sub");
        post("/api/projects", """
                {"name":"Patch Ziel","short_code":"PATCH"}""", token);

        Response patched = patch("/api/projects/PATCH", """
                {"name":"Patch Ziel neu","description":"Zeile eins\\n\\nZeile zwei"}""", token);

        assertThat(patched.status()).isEqualTo(200);
        assertThat(patched.body()).contains("\"name\":\"Patch Ziel neu\"");
        assertThat(patched.body()).contains("\"description_html\":\"<p>Zeile eins</p><p>Zeile zwei</p>\"");
        assertThat(patched.body()).contains("\"description_text\":\"Zeile eins Zeile zwei\"");

        Response activity = get("/api/activities?parent=project:PATCH", token);
        assertThat(activity.body()).contains("\"event_type\":\"updated\"").contains("name='Patch Ziel neu'");
    }

    @Test
    void sanitizesRichHtmlOnWrite() throws Exception {
        String token = bearerToken("it-rich-sub");
        post("/api/projects", """
                {"name":"Rich Ziel","short_code":"RICH"}""", token);

        Response patched = patch("/api/projects/RICH", """
                {"description_html":"<h2>Plan</h2><p>ok</p><script>steal()</script><b onclick=\\"x()\\">fett</b>"}""",
                token);

        assertThat(patched.status()).isEqualTo(200);
        assertThat(patched.body()).contains("<h2>Plan</h2>").contains("<b>fett</b>");
        assertThat(patched.body()).doesNotContain("<script").doesNotContain("onclick");
    }

    @Test
    void rejectsBothContentShapesAndPatchesWithoutChanges() throws Exception {
        String token = bearerToken("it-content-sub");
        post("/api/projects", """
                {"name":"Inhalt Ziel","short_code":"INHALT"}""", token);

        Response both = patch("/api/projects/INHALT", """
                {"description":"text","description_html":"<p>text</p>"}""", token);
        assertThat(both.status()).isEqualTo(400);
        assertThat(both.body()).contains("either description or description_html");

        Response nothing = patch("/api/projects/INHALT", """
                {"name":"Inhalt Ziel"}""", token);
        assertThat(nothing.status()).isEqualTo(400);
        assertThat(nothing.body()).contains("nothing to change");
    }

    @Test
    void createsLabelsOnDemandAndReplacesTheWholeSet() throws Exception {
        String token = bearerToken("it-label-sub");

        Response created = post("/api/projects", """
                {"name":"Label Ziel","short_code":"LABEL","labels":["Infra","MCP"]}""", token);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body()).contains("\"name\":\"infra\"").contains("\"name\":\"mcp\"");
        assertThat(created.body()).doesNotContain("\"name\":\"Infra\"");

        Response known = get("/api/labels", token);
        assertThat(known.body()).contains("\"name\":\"infra\"").contains("\"name\":\"mcp\"");

        Response replaced = patch("/api/projects/LABEL", """
                {"labels":["infra","docs"]}""", token);
        assertThat(replaced.body()).contains("\"name\":\"infra\"").contains("\"name\":\"docs\"");
        assertThat(replaced.body()).doesNotContain("\"name\":\"mcp\"");

        Response activity = get("/api/activities?parent=project:LABEL", token);
        assertThat(activity.body()).contains("\"event_type\":\"labeled\"").contains("\"event_type\":\"unlabeled\"");
        assertThat(activity.body()).contains("docs").contains("mcp");

        Response cleared = patch("/api/projects/LABEL", """
                {"labels":[]}""", token);
        assertThat(cleared.body()).contains("\"labels\":[]");
    }

    @Test
    void filtersTheLeanListByLabelAndQuery() throws Exception {
        String token = bearerToken("it-filter-sub");
        post("/api/projects", """
                {"name":"Filter Alpha","short_code":"FILA","description":"Suchmarke Alpha","labels":["filterlabel"]}""",
                token);
        post("/api/projects", """
                {"name":"Filter Beta","short_code":"FILB","description":"Suchmarke Beta"}""", token);

        Response byLabel = get("/api/projects?label=filterlabel", token);
        assertThat(byLabel.body()).contains("\"reference\":\"FILA\"").doesNotContain("\"reference\":\"FILB\"");

        Response byQuery = get("/api/projects?q=Suchmarke", token);
        assertThat(byQuery.body()).contains("\"reference\":\"FILA\"").contains("\"reference\":\"FILB\"");

        Response lean = get("/api/projects?q=Suchmarke", token);
        assertThat(lean.body()).contains("\"excerpt\":").doesNotContain("description_html");
    }

    @Test
    void rejectsBlankAndOverlongNames() throws Exception {
        String token = bearerToken("it-name-sub");

        Response blank = post("/api/projects", """
                {"name":"   ","short_code":"BLANK"}""", token);
        assertThat(blank.status()).isEqualTo(400);

        Response tooLong = post("/api/projects", """
                {"name":"%s","short_code":"LONG"}""".formatted("n".repeat(201)), token);
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(tooLong.body()).contains("name");
    }
}
