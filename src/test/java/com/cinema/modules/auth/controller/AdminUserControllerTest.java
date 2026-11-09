package com.cinema.modules.auth.controller;

import com.cinema.common.exception.AppException;
import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.auth.dto.request.UpdateUserRolesRequest;
import com.cinema.modules.auth.dto.request.UpdateUserStatusRequest;
import com.cinema.modules.auth.dto.response.AdminUserResponse;
import com.cinema.modules.auth.dto.response.AuditLogResponse;
import com.cinema.modules.auth.dto.response.BranchAssignmentResponse;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.auth.service.interfaces.IAdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private static final Long ACTOR_ID = 1L;

    private static final AdminUserResponse USER = new AdminUserResponse(
            30L, "nv@cinema.vn", "Nhân Viên", "0912345678", null, "ACTIVE", true, true, List.of("ROLE_STAFF"), "STAR",
            0, Instant.parse("2026-11-09T03:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), "NV001",
            "TICKETING_STAFF", "ACTIVE", null, List.of(new BranchAssignmentResponse(1L, "CinemaSphere Q1", true)));

    @Mock
    private IAdminUserService adminUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(adminUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(ACTOR_ID, "admin@cinema.vn", List.of("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String preAuthorizeOf(String methodName) {
        for (Method method : AdminUserController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
                return preAuthorize == null ? null : preAuthorize.value();
            }
        }
        throw new IllegalArgumentException(methodName);
    }

    @Test
    void classIsOpenToAdminAndManagerWhileSensitiveActionsAreAdminOnly() {
        PreAuthorize classLevel = AdminUserController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(classLevel);
        assertEquals("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')", classLevel.value());
        assertEquals("hasAuthority('ROLE_ADMIN')", preAuthorizeOf("updateRoles"));
        assertEquals("hasAuthority('ROLE_ADMIN')", preAuthorizeOf("deleteUser"));
        assertEquals("hasAuthority('ROLE_ADMIN')", preAuthorizeOf("getAuditLogs"));
    }

    @Test
    void searchPassesAllFiltersAndReturnsAPageWithoutSecrets() throws Exception {
        when(adminUserService.searchUsers(ACTOR_ID, "an", "ROLE_STAFF", UserStatus.ACTIVE, 2L, 1, 10))
                .thenReturn(new PageResponse<>(List.of(USER), 1, 10, 11, 2));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("search", "an").param("role", "ROLE_STAFF").param("status", "ACTIVE")
                        .param("cinemaId", "2").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].email").value("nv@cinema.vn"))
                .andExpect(jsonPath("$.data.content[0].branches[0].primary").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].tokenHash").doesNotExist());
    }

    @Test
    void searchUsesDefaultsAndRejectsAnUnknownStatus() throws Exception {
        when(adminUserService.searchUsers(ACTOR_ID, null, null, null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users").param("status", "BANANA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void getUserMapsNotFoundToA404Envelope() throws Exception {
        when(adminUserService.getUser(ACTOR_ID, 99L))
                .thenThrow(new AppException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        when(adminUserService.getUser(ACTOR_ID, 30L)).thenReturn(USER);

        mockMvc.perform(get("/api/v1/admin/users/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeCode").value("NV001"));
        mockMvc.perform(get("/api/v1/admin/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void updateStatusPassesTheStatusAndActor() throws Exception {
        when(adminUserService.updateStatus(eq(ACTOR_ID), eq(30L), any(UpdateUserStatusRequest.class), any()))
                .thenReturn(USER);

        mockMvc.perform(patch("/api/v1/admin/users/30/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái tài khoản thành công"));

        ArgumentCaptor<UpdateUserStatusRequest> captor = ArgumentCaptor.forClass(UpdateUserStatusRequest.class);
        verify(adminUserService).updateStatus(eq(ACTOR_ID), eq(30L), captor.capture(), any());
        assertEquals(UserStatus.SUSPENDED, captor.getValue().status());
    }

    @Test
    void updateStatusRejectsMissingOrUnknownStatuses() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/30/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/admin/users/30/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANANA\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void updateRolesAcceptsExactlyOneValidRole() throws Exception {
        when(adminUserService.updateRoles(eq(ACTOR_ID), eq(30L), any(UpdateUserRolesRequest.class), any()))
                .thenReturn(USER);

        mockMvc.perform(put("/api/v1/admin/users/30/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_MANAGER\"]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateUserRolesRequest> captor = ArgumentCaptor.forClass(UpdateUserRolesRequest.class);
        verify(adminUserService).updateRoles(eq(ACTOR_ID), eq(30L), captor.capture(), any());
        assertEquals(List.of("ROLE_MANAGER"), captor.getValue().roles());
    }

    @Test
    void updateRolesRejectsEmptyMultipleOrUnknownRoles() throws Exception {
        for (String body : List.of("{\"roles\":[]}", "{}", "{\"roles\":[\"ROLE_STAFF\",\"ROLE_ADMIN\"]}",
                "{\"roles\":[\"ROLE_ROOT\"]}", "{\"roles\":[\"admin\"]}")) {
            mockMvc.perform(put("/api/v1/admin/users/30/roles").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        verify(adminUserService, never()).updateRoles(any(), any(), any(), any());
    }

    @Test
    void updateUserInfoValidatesNameAndPhone() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/30").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"phone\":\"0912345678\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/users/30").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"An\",\"phone\":\"abc\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void deleteUserReturnsOkAndPassesTheActor() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa tài khoản thành công"));

        verify(adminUserService).deleteUser(eq(ACTOR_ID), eq(30L), any());
    }

    @Test
    void deleteUserSurfacesBusinessRuleViolations() throws Exception {
        doThrow(new AppException("Hệ thống phải luôn còn ít nhất một Admin đang hoạt động",
                        HttpStatus.CONFLICT, "LAST_ADMIN"))
                .when(adminUserService).deleteUser(eq(ACTOR_ID), eq(30L), any());

        mockMvc.perform(delete("/api/v1/admin/users/30"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LAST_ADMIN"));
    }

    @Test
    void auditLogsReturnAPageOfEntries() throws Exception {
        AuditLogResponse auditLogResponse = new AuditLogResponse(
                7L, 1L, "Admin", 30L, "STATUS_CHANGED", Map.of("before", "ACTIVE", "after", "SUSPENDED"), "10.0.0.1",
                Instant.parse("2026-11-09T03:00:00Z"));
        when(adminUserService.getAuditLogs(ACTOR_ID, 30L, 0, 20))
                .thenReturn(new PageResponse<>(List.of(auditLogResponse), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/admin/users/30/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].action").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.data.content[0].details.after").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.content[0].actorName").value("Admin"));
    }
}
