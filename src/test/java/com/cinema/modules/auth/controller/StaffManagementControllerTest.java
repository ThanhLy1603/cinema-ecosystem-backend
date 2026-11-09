package com.cinema.modules.auth.controller;

import com.cinema.common.exception.AppException;
import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.auth.dto.request.AssignBranchesRequest;
import com.cinema.modules.auth.dto.request.CreateStaffRequest;
import com.cinema.modules.auth.dto.response.BranchAssignmentResponse;
import com.cinema.modules.auth.dto.response.StaffResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.auth.service.StaffManagementService;
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

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StaffManagementControllerTest {

    private static final Long ADMIN_ID = 1L;

    private static final StaffResponse STAFF = new StaffResponse(
            20L, "nv@cinema.vn", "Nhân Viên", "0912345678", "ACTIVE", false, List.of("ROLE_STAFF"), "NV001",
            "TICKETING_STAFF", "ACTIVE", LocalDate.of(2026, 1, 15),
            List.of(new BranchAssignmentResponse(1L, "CinemaSphere Q1", true)));

    private static final String VALID_CREATE_BODY = """
            {"email":"nv@cinema.vn","password":"Matkhau123","fullName":"Nhân Viên","phone":"0912345678",
             "role":"ROLE_STAFF","employeeCode":"NV001","position":"TICKETING_STAFF","hiredAt":"2026-01-15",
             "cinemaIds":[1,2],"primaryCinemaId":1}""";

    @Mock
    private StaffManagementService staffManagementService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StaffManagementController(staffManagementService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(ADMIN_ID, "admin@cinema.vn", List.of("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void controllerIsRestrictedToAdminOnly() {
        PreAuthorize preAuthorize = StaffManagementController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("hasAuthority('ROLE_ADMIN')", preAuthorize.value());
    }

    @Test
    void createStaffReturns201WithTheCreatedProfileAndPassesTheAdminId() throws Exception {
        when(staffManagementService.createStaff(eq(ADMIN_ID), any(CreateStaffRequest.class), any()))
                .thenReturn(STAFF);

        mockMvc.perform(post("/api/v1/admin/staff").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(20))
                .andExpect(jsonPath("$.data.employeeCode").value("NV001"))
                .andExpect(jsonPath("$.data.hiredAt").value("2026-01-15"))
                .andExpect(jsonPath("$.data.branches[0].cinemaName").value("CinemaSphere Q1"))
                .andExpect(jsonPath("$.data.branches[0].primary").value(true))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        ArgumentCaptor<CreateStaffRequest> captor = ArgumentCaptor.forClass(CreateStaffRequest.class);
        verify(staffManagementService).createStaff(eq(ADMIN_ID), captor.capture(), any());
        assertEquals(List.of(1L, 2L), captor.getValue().cinemaIds());
        assertEquals(1L, captor.getValue().primaryCinemaId());
    }

    @Test
    void createStaffRejectsRolesOtherThanStaffOrManager() throws Exception {
        mockMvc.perform(post("/api/v1/admin/staff").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY.replace("ROLE_STAFF", "ROLE_ADMIN")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(staffManagementService);
    }

    @Test
    void createStaffRequiresAtLeastOneBranch() throws Exception {
        mockMvc.perform(post("/api/v1/admin/staff").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY.replace("[1,2]", "[]")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/staff").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY.replace("\"cinemaIds\":[1,2],", "")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(staffManagementService);
    }

    @Test
    void createStaffRejectsWeakPasswordsAndBadEmployeeCodes() throws Exception {
        mockMvc.perform(post("/api/v1/admin/staff").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY.replace("Matkhau123", "abc")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/staff").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY.replace("NV001", "NV 001!")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(staffManagementService);
    }

    @Test
    void getStaffReturnsTheProfile() throws Exception {
        when(staffManagementService.getStaff(20L)).thenReturn(STAFF);

        mockMvc.perform(get("/api/v1/admin/staff/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("nv@cinema.vn"))
                .andExpect(jsonPath("$.data.staffStatus").value("ACTIVE"));
    }

    @Test
    void getStaffMapsNotFoundToA404Envelope() throws Exception {
        when(staffManagementService.getStaff(99L))
                .thenThrow(new AppException("Không tìm thấy nhân viên", HttpStatus.NOT_FOUND, "STAFF_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/admin/staff/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("STAFF_NOT_FOUND"));
    }

    @Test
    void assignBranchesPassesTheValidatedBodyAndTheAdminId() throws Exception {
        when(staffManagementService.assignBranches(eq(ADMIN_ID), eq(20L), any(AssignBranchesRequest.class), any()))
                .thenReturn(STAFF);

        mockMvc.perform(put("/api/v1/admin/staff/20/branches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cinemaIds\":[1,3],\"primaryCinemaId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Gán chi nhánh thành công"));

        ArgumentCaptor<AssignBranchesRequest> captor = ArgumentCaptor.forClass(AssignBranchesRequest.class);
        verify(staffManagementService).assignBranches(eq(ADMIN_ID), eq(20L), captor.capture(), any());
        assertEquals(List.of(1L, 3L), captor.getValue().cinemaIds());
    }

    @Test
    void assignBranchesRejectsAnEmptyOrInvalidBranchList() throws Exception {
        mockMvc.perform(put("/api/v1/admin/staff/20/branches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cinemaIds\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/staff/20/branches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cinemaIds\":[0]}"))
                .andExpect(status().isBadRequest());

        verify(staffManagementService, never()).assignBranches(any(), any(), any(), any());
    }

    @Test
    void updateStaffRejectsAMissingStatus() throws Exception {
        mockMvc.perform(put("/api/v1/admin/staff/20").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_STAFF\",\"employeeCode\":\"NV001\",\"position\":\"AUDITOR\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(staffManagementService);
    }
}
