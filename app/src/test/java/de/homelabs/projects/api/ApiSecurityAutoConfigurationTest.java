package de.homelabs.projects.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSecurityAutoConfigurationTest {

    @Test
    void acceptsATokenIssuedForAnyConfiguredAudience() {
        OAuth2TokenValidator<Jwt> validator = ApiSecurityAutoConfiguration.audienceValidator(
                List.of("https://mcp.homelabs.de/mcp", "https://projects.homelabs.de"));

        assertThat(result(validator, List.of("https://mcp.homelabs.de/mcp")).hasErrors()).isFalse();
        assertThat(result(validator, List.of("https://projects.homelabs.de", "other")).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokensForAnotherResource() {
        OAuth2TokenValidator<Jwt> validator = ApiSecurityAutoConfiguration.audienceValidator(
                List.of("https://mcp.homelabs.de/mcp"));

        assertThat(result(validator, List.of("https://evil.example/mcp")).hasErrors()).isTrue();
        assertThat(result(validator, List.of()).hasErrors()).isTrue();
    }

    private static OAuth2TokenValidatorResult result(OAuth2TokenValidator<Jwt> validator, List<String> audiences) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("sub")
                .claim("aud", audiences)
                .build();
        return validator.validate(jwt);
    }
}
