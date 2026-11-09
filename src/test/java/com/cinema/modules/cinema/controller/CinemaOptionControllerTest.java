package com.cinema.modules.cinema.controller;

import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.cinema.dto.response.CinemaOptionResponse;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CinemaOptionControllerTest {

    private static final Long USER_ID = 5L;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    @Mock
    private IBranchAccessService branchAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CinemaOptionController(cinemaQueryService, branchAccessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(USER_ID, "u@cinema.vn", List.of("ROLE_MANAGER"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, List.of()));
        lenient().when(cinemaQueryService.findActiveCinemaOptions()).thenReturn(List.of(
                new CinemaOptionResponse(1L, "Q1", "HCM"), new CinemaOptionResponse(2L, "Q7", "HCM")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void controllerIsOpenToAdminAndManagerOnly() {
        PreAuthorize preAuthorize = CinemaOptionController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')", preAuthorize.value());
    }

    @Test
    void adminsGetEveryActiveCinema() throws Exception {
        when(branchAccessService.hasAccessToAllCinemas(USER_ID)).thenReturn(true);

        mockMvc.perform(get("/api/v1/admin/cinema-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].name").value("Q7"));

        verify(branchAccessService, never()).getAccessibleCinemaIds(USER_ID);
    }

    @Test
    void managersOnlyGetTheCinemasTheyManage() throws Exception {
        when(branchAccessService.hasAccessToAllCinemas(USER_ID)).thenReturn(false);
        when(branchAccessService.getAccessibleCinemaIds(USER_ID)).thenReturn(Set.of(2L));

        mockMvc.perform(get("/api/v1/admin/cinema-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(2));
    }
}
