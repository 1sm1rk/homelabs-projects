package de.homelabs.projects;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Shared harness of the API integration tests: test-token minting, plain HTTP calls
 * against the REAL REST surface, a JDBC handle for the assertions that must look
 * behind the API, and the identifier helpers.
 *
 * <p>Every test class declares its own {@code @Container}/{@code @DynamicPropertySource}
 * pair (the established pattern in this codebase), so each class runs against a fresh
 * database; within a class the database is shared, which is why assertions are by
 * unique marker and never {@code containsExactly}.
 */
abstract class ApiTestSupport {

    static final String SOURCE_HEADER = "X-Mcp-Source";

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JdbcClient jdbc;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final RestClient restClient = RestClient.create();

    record Response(int status, String body) {
    }

    String bearerToken(String sub) {
        return token(sub, TestSecurityConfig.AUDIENCE, null);
    }

    /** A token whose display-name claim differs from the subject. */
    String bearerToken(String sub, String username) {
        return token(sub, TestSecurityConfig.AUDIENCE, username);
    }

    String token(String sub, String audience, String username) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("test-issuer")
                .subject(sub)
                .audience(List.of(audience))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        if (username != null) {
            claims.claim("preferred_username", username);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    Response get(String path, String token) throws Exception {
        return send(request(path, token, null).GET().build());
    }

    Response post(String path, String json, String token) throws Exception {
        return post(path, json, token, null);
    }

    Response post(String path, String json, String token, String source) throws Exception {
        return send(request(path, token, source)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    Response patch(String path, String json, String token) throws Exception {
        return send(request(path, token, null)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    Response delete(String path, String token) throws Exception {
        return send(request(path, token, null).DELETE().build());
    }

    HttpRequest.Builder request(String path, String token, String source) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(Duration.ofSeconds(30));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (source != null) {
            builder.header(SOURCE_HEADER, source);
        }
        return builder;
    }

    URI uri(String path) {
        return URI.create("http://localhost:%d%s".formatted(port, path));
    }

    Response send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }

    /**
     * One multipart upload (the attachment endpoint is the only one): plain
     * {@code RestClient}, and a status/body answer even for 4xx so the failure paths are
     * assertable.
     */
    Response upload(String path, String token, String parent, String filename, String contentType, byte[] bytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("parent", parent);
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        RestClient.RequestBodySpec request = restClient.post()
                .uri(uri(path))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.exchange((sent, response) -> new Response(response.getStatusCode().value(),
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    /** A download with the response headers the attachment content endpoint promises. */
    BinaryResponse download(String path, String token) {
        return restClient.get()
                .uri(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((sent, response) -> new BinaryResponse(
                        response.getStatusCode().value(),
                        response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                        response.getHeaders().getFirst(HttpHeaders.ETAG),
                        response.getBody().readAllBytes()));
    }

    record BinaryResponse(int status, String contentType, String etag, byte[] body) {
    }

    /** The first UUID of an {@code "id"} field in a response body. */
    static UUID uuidIn(String body) {
        Matcher matcher = ID.matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("no \"id\" in: " + body);
        }
        return UUID.fromString(matcher.group(1));
    }
}
