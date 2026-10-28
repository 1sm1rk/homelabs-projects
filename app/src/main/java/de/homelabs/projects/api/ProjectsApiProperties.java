package de.homelabs.projects.api;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the REST API's OAuth 2.0 resource server. The API's callers are
 * configured servers (now the MCP gateway, later the frontend's browser client), not
 * arbitrary browsers, so there is no Protected Resource Metadata document and no
 * authorization-server discovery: issuer and accepted audiences are pinned here.
 *
 * <p>The audiences are the RESOURCES a token may have been issued for (RFC 8707
 * resource-location binding), never user authorization — the user boundary is
 * carried by {@code owner}/{@code assignee} sub columns. The property is a list from
 * day one so the frontend project appends its own audience without a structural
 * change; a token is accepted when its {@code aud} contains at least one of them.
 *
 * @param issuerUri the issuer whose metadata resolves the JWK set and whose
 *                  {@code iss} claim tokens must carry
 * @param audiences the accepted {@code aud} values
 */
@ConfigurationProperties(prefix = "projects.api")
public record ProjectsApiProperties(
        @DefaultValue("http://localhost:8091/realms/homelabs") String issuerUri,
        @DefaultValue("http://localhost:8090/mcp") List<String> audiences) {

    public ProjectsApiProperties {
        audiences = List.copyOf(audiences);
    }
}
