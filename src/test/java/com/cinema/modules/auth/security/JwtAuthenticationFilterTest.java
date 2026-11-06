package com.cinema.modules.auth.security;

import com.cinema.config.JwtProperties;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
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
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            httpServletRequest.addHeader("Authorization", authorizationHeader);
        }
        MockFilterChain filterChain = new MockFilterChain();
        jwtAuthenticationFilter.doFilter(httpServletRequest, new MockHttpServletResponse(), filterChain);
        return filterChain;
    }

    @Test
    void validBearerTokenAuthenticatesWithRolesAsAuthorities() throws Exception {
        MockFilterChain filterChain = run("Bearer " + accessToken);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(true, authentication.isAuthenticated());
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        assertEquals(7L, authenticatedUser.userId());
        assertEquals(List.of(Role.STAFF),
                authentication.getAuthorities().stream().map(Object::toString).toList());
        assertNotNull(filterChain.getRequest(), "request phải đi tiếp trong chuỗi filter");
    }

    @Test
    void invalidTokenLeavesRequestUnauthenticatedButContinuesChain() throws Exception {
        MockFilterChain filterChain = run("Bearer " + accessToken + "tampered");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(filterChain.getRequest());
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
}
