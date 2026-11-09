package com.cinema.modules.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String CSRF_GUARD_HEADER = "X-Requested-With";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String accessToken = resolveAccessToken(httpServletRequest);
            if (accessToken != null) {
                jwtTokenProvider.parseAccessToken(accessToken)
                        .ifPresent(authenticatedUser -> authenticate(authenticatedUser, httpServletRequest));
            }
        }
        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    private void authenticate(AuthenticatedUser authenticatedUser, HttpServletRequest httpServletRequest) {
        List<SimpleGrantedAuthority> simpleGrantedAuthorities =
                authenticatedUser.roles().stream().map(SimpleGrantedAuthority::new).toList();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, simpleGrantedAuthorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpServletRequest));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveAccessToken(HttpServletRequest httpServletRequest) {
        String bearerToken = extractBearerToken(httpServletRequest);
        if (bearerToken != null) {
            return bearerToken;
        }
        if (!isCsrfGuardSatisfied(httpServletRequest)) {
            return null;
        }
        return extractCookieToken(httpServletRequest);
    }

    private boolean isCsrfGuardSatisfied(HttpServletRequest httpServletRequest) {
        if (SAFE_METHODS.contains(httpServletRequest.getMethod())) {
            return true;
        }
        String csrfGuardHeader = httpServletRequest.getHeader(CSRF_GUARD_HEADER);
        return csrfGuardHeader != null && !csrfGuardHeader.isBlank();
    }

    private String extractBearerToken(HttpServletRequest httpServletRequest) {
        String authorizationHeader = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            String accessToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
            return accessToken.isEmpty() ? null : accessToken;
        }
        return null;
    }

    private String extractCookieToken(HttpServletRequest httpServletRequest) {
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AccessTokenCookieFactory.COOKIE_NAME.equals(cookie.getName())) {
                String accessToken = cookie.getValue();
                return accessToken == null || accessToken.isBlank() ? null : accessToken;
            }
        }
        return null;
    }
}
