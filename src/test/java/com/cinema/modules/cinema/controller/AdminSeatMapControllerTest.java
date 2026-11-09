package com.cinema.modules.cinema.controller;

import com.cinema.common.exception.AppException;
import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.cinema.dto.request.GenerateSeatsRequest;
import com.cinema.modules.cinema.dto.request.SeatMapRequest;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.dto.response.SeatLayoutSeatResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.enums.SeatStatus;
import com.cinema.modules.cinema.service.interfaces.ISeatMapService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminSeatMapControllerTest {

    private static final Long USER_ID = 5L;
    private static final Long HALL_ID = 12L;

    @Mock
    private ISeatMapService seatMapService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminSeatMapController(seatMapService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(USER_ID, "m@cinema.vn", List.of("ROLE_MANAGER"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void controllerIsOpenToAdminAndManagerOnly() {
        PreAuthorize preAuthorize = AdminSeatMapController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')", preAuthorize.value());
    }

    @Test
    void getSeatMapReturnsTheLayoutContract() throws Exception {
        when(seatMapService.getSeatMap(USER_ID, HALL_ID)).thenReturn(layout());

        mockMvc.perform(get("/api/v1/admin/halls/12/seat-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hallId").value(12))
                .andExpect(jsonPath("$.data.screenType").value("IMAX"))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.rows[0]").value("A"))
                .andExpect(jsonPath("$.data.columns").value(3))
                .andExpect(jsonPath("$.data.seats[0].type").value("SWEETBOX"))
                .andExpect(jsonPath("$.data.seats[0].span").value(2))
                .andExpect(jsonPath("$.data.seats[0].surcharge").value(45000));
    }

    @Test
    void saveSeatMapPassesTheRequestToTheService() throws Exception {
        when(seatMapService.saveSeatMap(eq(USER_ID), eq(HALL_ID), any(SeatMapRequest.class))).thenReturn(layout());

        mockMvc.perform(put("/api/v1/admin/halls/12/seat-map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":4,"seats":[{"row":"A","number":1,"seatTypeCode":"SWEETBOX","status":"ACTIVE"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lưu sơ đồ ghế thành công"));

        verify(seatMapService).saveSeatMap(eq(USER_ID), eq(HALL_ID), any(SeatMapRequest.class));
    }

    @Test
    void saveSeatMapRejectsInvalidRowBeforeReachingTheService() throws Exception {
        mockMvc.perform(put("/api/v1/admin/halls/12/seat-map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":4,"seats":[{"row":"a1","number":0,"seatTypeCode":"VIP","status":"ACTIVE"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(seatMapService);
    }

    @Test
    void saveSeatMapRequiresAVersion() throws Exception {
        mockMvc.perform(put("/api/v1/admin/halls/12/seat-map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seats\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void saveSeatMapReportsVersionConflictAs409() throws Exception {
        when(seatMapService.saveSeatMap(eq(USER_ID), eq(HALL_ID), any(SeatMapRequest.class)))
                .thenThrow(new AppException("Đã bị cập nhật", HttpStatus.CONFLICT, "SEAT_MAP_CONFLICT"));

        mockMvc.perform(put("/api/v1/admin/halls/12/seat-map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"seats\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAT_MAP_CONFLICT"));
    }

    @Test
    void generateSeatsReturnsCreated() throws Exception {
        when(seatMapService.generateSeats(eq(USER_ID), eq(HALL_ID), any(GenerateSeatsRequest.class)))
                .thenReturn(layout());

        mockMvc.perform(post("/api/v1/admin/halls/12/generate-seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowCount\":10,\"columnCount\":10,\"seatTypeCode\":\"STANDARD\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void generateSeatsRejectsMoreThan26Rows() throws Exception {
        mockMvc.perform(post("/api/v1/admin/halls/12/generate-seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowCount\":27,\"columnCount\":10,\"seatTypeCode\":\"STANDARD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    private static SeatLayoutResponse layout() {
        SeatLayoutSeatResponse seatLayoutSeatResponse = new SeatLayoutSeatResponse(
                101L, "A", 2, "SWEETBOX", BigDecimal.valueOf(45000), 2, SeatStatus.ACTIVE);
        return new SeatLayoutResponse(
                12L, 2L, "Phòng 01 - IMAX", ScreenType.IMAX, HallStatus.ACTIVE, 4L, List.of("A"), 3,
                List.of(seatLayoutSeatResponse));
    }
}
