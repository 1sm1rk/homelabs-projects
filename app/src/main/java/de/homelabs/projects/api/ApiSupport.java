package de.homelabs.projects.api;

import org.springframework.security.oauth2.jwt.Jwt;

import de.homelabs.projects.domain.Caller;

/**
 * Shared plumbing of the REST surface: the internal harness header, the caller
 * resolution from the token, and the result-limit window every list endpoint applies.
 */
final class ApiSupport {

    /**
     * Internal header carrying the MCP client identity (the harness name from the MCP
     * initialize handshake).
     *
     * <p>{@code source} stays SERVER-DERIVED: the MCP edge — the projects adapter —
     * derives it from {@code clientInfo} and writes it into this header; it never
     * appears in a request body, so no caller can claim an arbitrary source through
     * payload fields. Direct API callers (no header) record no source at all.
     */
    static final String SOURCE_HEADER = "X-Mcp-Source";

    /** List defaults: the window agents get when they do not ask for a size. */
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    /** Same window for comments and activity: newest first, bounded. */
    static final int DEFAULT_ACTIVITY_LIMIT = 50;
    static final int MAX_ACTIVITY_LIMIT = 200;

    /**
     * The calling principal: {@code sub} is the user boundary, {@code preferred_username}
     * the display-name snapshot (falling back to the sub), {@code source} the harness
     * identity when the request came through the MCP edge.
     */
    static Caller caller(Jwt jwt, String sourceHeader) {
        String sub = jwt.getSubject();
        return new Caller(sub, username(jwt), (sourceHeader == null || sourceHeader.isBlank())
                ? null
                : sourceHeader.trim());
    }

    /** The display name claim, falling back to the subject. */
    static String username(Jwt jwt) {
        String preferred = jwt.getClaimAsString("preferred_username");
        return (preferred == null || preferred.isBlank()) ? jwt.getSubject() : preferred;
    }

    /** Default when absent, clamped to [1, max]. */
    static int clamp(Integer value, int defaultValue, int maxValue) {
        int limit = (value == null) ? defaultValue : value;
        return Math.min(Math.max(limit, 1), maxValue);
    }

    private ApiSupport() {
    }
}
