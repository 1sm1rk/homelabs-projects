package de.homelabs.projects.api;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import de.homelabs.projects.domain.UserDirectory;

/**
 * Keeps the local user directory fresh: on EVERY authenticated request the principal
 * is upserted into {@code app_user} ({@code sub} plus the {@code preferred_username}
 * snapshot, first/last seen). Display names are resolved from that table, so the
 * service needs no Keycloak admin-API dependency.
 *
 * <p>Runs after the security filter chain (a plain servlet filter has the lower
 * precedence), so the authenticated principal is already in the context; open paths
 * such as {@code GET /health} carry no principal and are skipped.
 */
@Component
public class AppUserFilter extends OncePerRequestFilter {

    private final UserDirectory users;

    public AppUserFilter(UserDirectory users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            users.touch(jwt.getSubject(), ApiSupport.username(jwt));
        }
        chain.doFilter(request, response);
    }
}
