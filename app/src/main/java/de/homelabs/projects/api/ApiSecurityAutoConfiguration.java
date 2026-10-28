package de.homelabs.projects.api;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Wires the REST API's OAuth 2.0 resource server: issuer and audience validation on
 * every request, one exception for the unauthenticated liveness probe
 * ({@code GET /health}).
 *
 * <p>An auto-configuration rather than a plain {@code @Configuration} so the
 * {@code @ConditionalOnMissingBean} rules let a test slice replace the decoder — the
 * integration tests mint their own tokens without a running authorization server.
 *
 * <p>The issuer metadata (and with it the JWK set) is resolved lazily on the first
 * token, so the service starts even while its authorization server is still coming
 * up.
 */
@AutoConfiguration(before = { OAuth2ResourceServerAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiSecurityAutoConfiguration {

    /** JWT validation bound to issuer and the accepted audience list. */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder projectsJwtDecoder(ProjectsApiProperties properties) {
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(properties.issuerUri()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                    new JwtIssuerValidator(properties.issuerUri()),
                    audienceValidator(properties.audiences())));
            return decoder;
        });
    }

    /**
     * The audience binding: the token's {@code aud} claim must contain at least one
     * of the accepted audiences. Any-match is the multi-tenant reading of a resource
     * server with several registered clients — one token is issued for one resource.
     */
    public static OAuth2TokenValidator<Jwt> audienceValidator(List<String> audiences) {
        return new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                values -> values != null && values.stream().anyMatch(audiences::contains));
    }

    /**
     * Stateless JWT API: no session, no CSRF (no cookies). Everything under
     * {@code /api/**} requires a valid token; only the liveness probe is open, so a
     * container healthcheck needs no credentials.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain projectsApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
