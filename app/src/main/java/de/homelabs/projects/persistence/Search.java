package de.homelabs.projects.persistence;

/**
 * Shared lexical-search plumbing of the three arms used by every find endpoint:
 * ILIKE (escaped pattern), German full-text search and pg_trgm word similarity.
 *
 * <p>Only the LIKE arm needs building; the other two take the raw query. The trigram
 * arm carries the German verb variants that the stemmer misses ({@code speichert} vs
 * {@code speichere}) plus typos.
 */
final class Search {

    /** Characters of derived plain text a lean list line carries. */
    static final int EXCERPT_LENGTH = 200;

    /** Builds the escaped {@code %query%} pattern for the ILIKE arm. */
    static String like(String query) {
        String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private Search() {
    }
}
