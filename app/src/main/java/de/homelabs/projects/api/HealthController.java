package de.homelabs.projects.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open liveness probe for container healthchecks: it returns no project data, so it
 * needs no credentials. Everything under {@code /api/**} requires a token.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "homelabs-projects");
    }

    public record HealthResponse(String status, String service) {
    }
}
