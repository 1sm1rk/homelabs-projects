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
 * The three lexical search arms over projects, work packages and tasks: plain
 * substring (ILIKE), German full-text search, and pg_trgm word similarity for the
 * cases the stemmer misses — verb forms ({@code speichert} vs {@code speichere}) and
 * typos. Test contents are lexically DISTINCT and every assertion names its reference,
 * never a whole-collection match: the Testcontainers database is shared per class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestSecurityConfig.class)
class SearchArmsIT extends ApiTestSupport {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void findsProjectsBySubstringAndByTypo() throws Exception {
        String token = bearerToken("it-search-project-sub");
        post("/api/projects", """
                {"name":"Suchtiefe","short_code":"SUCH",
                 "description":"Das Projekt speichert die Anforderungen der Werkstatt."}""", token);

        // substring arm
        assertThat(get("/api/projects?q=Werkstatt", token).body()).contains("\"reference\":\"SUCH\"");

        // trigram arm: verb form the German stemmer does not unify with 'speichert'
        assertThat(get("/api/projects?q=speichere", token).body()).contains("\"reference\":\"SUCH\"");

        // trigram arm: typo inside the word
        assertThat(get("/api/projects?q=Werkstat", token).body()).contains("\"reference\":\"SUCH\"");
    }

    @Test
    void findsWorkPackagesAndTasksByAllThreeArms() throws Exception {
        String token = bearerToken("it-search-item-sub");
        post("/api/projects", """
                {"name":"Recherche","short_code":"RECH"}""", token);
        post("/api/projects/RECH/work-packages", """
                {"title":"Der Bericht speichert die Anfrage","description":"Protokoll der Sitzung."}""", token);
        post("/api/tasks", """
                {"project":"RECH","title":"Dokument importieren","description":"Der Import prüft die Datei."}""",
                token);

        // ILIKE: exact substring of the title
        assertThat(get("/api/work-packages?q=Bericht", token).body()).contains("\"reference\":\"RECH-1\"");

        // FTS/trigram: 'speichere' matches 'speichert' only through the trigram arm
        assertThat(get("/api/work-packages?q=speichere", token).body()).contains("\"reference\":\"RECH-1\"");
        assertThat(get("/api/work-packages?q=Dokumnt", token).body()).doesNotContain("\"reference\":\"RECH-2\"");

        // task search hits its own text only
        assertThat(get("/api/tasks?q=Dokument", token).body()).contains("\"reference\":\"RECH-2\"");
        assertThat(get("/api/tasks?q=Dokumnt", token).body()).contains("\"reference\":\"RECH-2\"");
        assertThat(get("/api/tasks?q=Bericht", token).body()).doesNotContain("\"reference\":\"RECH-2\"");
    }

    @Test
    void searchCombinesWithTheOtherFilters() throws Exception {
        String token = bearerToken("it-search-filter-sub");
        post("/api/projects", """
                {"name":"Kombination","short_code":"KOMB"}""", token);
        post("/api/projects/KOMB/work-packages", """
                {"title":"Paket Speicherung"}""", token);
        post("/api/projects/KOMB/work-packages", """
                {"title":"Paket Speicherung alt"}""", token);
        assertThat(patch("/api/work-packages/KOMB-1", """
                {"status":"in_progress"}""", token).status()).isEqualTo(200);
        assertThat(patch("/api/work-packages/KOMB-2", """
                {"status":"done"}""", token).status()).isEqualTo(200);

        Response combined = get("/api/work-packages?q=Speicherung&status=in_progress", token);

        assertThat(combined.body()).contains("\"reference\":\"KOMB-1\"");
        assertThat(combined.body()).doesNotContain("\"reference\":\"KOMB-2\"");
    }
}
