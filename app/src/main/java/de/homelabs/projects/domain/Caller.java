package de.homelabs.projects.domain;

/**
 * Who is calling: the authenticated principal plus the harness identity, resolved
 * once per request and passed into the services.
 *
 * @param sub      the token's {@code sub} — the user boundary on every write
 * @param username the {@code preferred_username} snapshot (falls back to the sub)
 * @param source   the harness client identity the MCP edge forwarded
 *                 ({@code X-Mcp-Source}), {@code null} for direct API callers
 */
public record Caller(String sub, String username, String source) {

    /**
     * The assignee filter: {@code null} or blank means "no filter", the literal
     * {@code me} resolves to the caller's own subject, anything else is taken as a
     * subject.
     */
    public String assigneeSub(String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String value = requested.trim();
        return "me".equalsIgnoreCase(value) ? sub : value;
    }
}
