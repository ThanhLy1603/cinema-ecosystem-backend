package com.cinema.modules.auth.security;

import com.cinema.config.properties.JwtProperties;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private String accessToken;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                new JwtProperties("0123456789abcdef0123456789abcdef0123456789abcdef", 900, 604800),
                Clock.fixed(Instant.now(), ZoneOffset.UTC));
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);

        User user = new User();
        user.setId(7L);
        user.setEmail("staff@cinema.vn");
        Role role = new Role();
        role.setCode(Role.STAFF);
        user.addRole(role);
        accessToken = jwtTokenProvider.generateAccessToken(user);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain run(String authorizationHeader) throws Exception {
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            mockHttpServletRequest.addHeader("Authorization", authorizationHeader);
        }
        return runRequest(mockHttpServletRequest);
    }

    private MockFilterChain runWithCookie(String method, String cookieValue, boolean withCsrfGuardHeader)
            throws Exception {
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest(method, "/api/v1/something");
        mockHttpServletRequest.setCookies(new Cookie(AccessTokenCookieFactory.COOKIE_NAME, cookieValue));
        if (withCsrfGuardHeader) {
            mockHttpServletRequest.addHeader(JwtAuthenticationFilter.CSRF_GUARD_HEADER, "XMLHttpRequest");
        }
        return runRequest(mockHttpServletRequest);
    }

    private MockFilterChain runRequest(MockHttpServletRequest mockHttpServletRequest) throws Exception {
        MockFilterChain mockFilterChain = new MockFilterChain();
        jwtAuthenticationFilter.doFilter(mockHttpServletRequest, new MockHttpServletResponse(), mockFilterChain);
        return mockFilterChain;
    }

    @Test
    void validBearerTokenAuthenticatesWithRolesAsAuthorities() throws Exception {
        MockFilterChain mockFilterChain = run("Bearer " + accessToken);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(true, authentication.isAuthenticated());
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        assertEquals(7L, authenticatedUser.userId());
        assertEquals(List.of(Role.STAFF),
                authentication.getAuthorities().stream().map(Object::toString).toList());
        assertNotNull(mockFilterChain.getRequest(), "request phải đi tiếp trong chuỗi filter");
    }

    @Test
    void invalidTokenLeavesRequestUnauthenticatedButContinuesChain() throws Exception {
        MockFilterChain mockFilterChain = run("Bearer " + accessToken + "tampered");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(mockFilterChain.getRequest());
    }

    @Test
    void missingOrNonBearerHeaderIsIgnored() throws Exception {
        assertNotNull(run(null).getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        assertNotNull(run("Basic dXNlcjpwYXNz").getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        assertNotNull(run("Bearer ").getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void accessTokenCookieAuthenticatesSafeRequestsWithoutCsrfHeader() throws Exception {
        runWithCookie("GET", accessToken, false);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(7L, ((AuthenticatedUser) authentication.getPrincipal()).userId());
    }

    @Test
    void accessTokenCookieOnStateChangingRequestNeedsTheCsrfGuardHeader() throws Exception {
        MockFilterChain mockFilterChain = runWithCookie("POST", accessToken, false);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(mockFilterChain.getRequest());
    }

    @Test
    void accessTokenCookieOnStateChangingRequestWithCsrfGuardHeaderAuthenticates() throws Exception {
        runWithCookie("POST", accessToken, true);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidAccessTokenCookieIsIgnored() throws Exception {
        runWithCookie("GET", accessToken + "tampered", true);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void bearerHeaderTakesPrecedenceOverCookie() throws Exception {
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest("GET", "/api/v1/something");
        mockHttpServletRequest.addHeader("Authorization", "Bearer " + accessToken + "tampered");
        mockHttpServletRequest.setCookies(new Cookie(AccessTokenCookieFactory.COOKIE_NAME, accessToken));

        runRequest(mockHttpServletRequest);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
