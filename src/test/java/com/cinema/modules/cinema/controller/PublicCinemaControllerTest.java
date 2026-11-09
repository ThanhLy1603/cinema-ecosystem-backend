package com.cinema.modules.cinema.controller;

import com.cinema.common.exception.AppException;
import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.cinema.dto.response.CinemaDetailResponse;
import com.cinema.modules.cinema.dto.response.CinemaResponse;
import com.cinema.modules.cinema.dto.response.FacilityResponse;
import com.cinema.modules.cinema.dto.response.HallResponse;
import com.cinema.modules.cinema.enums.CinemaStatus;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.service.interfaces.ICinemaPublicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicCinemaControllerTest {

    @Mock
    private ICinemaPublicService cinemaPublicService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicCinemaController(cinemaPublicService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listFiltersByCityAndExposesFacilitiesAndCoordinates() throws Exception {
        FacilityResponse facilityResponse = new FacilityResponse(1L, "Bãi đỗ xe ô tô", null, null, false);
        CinemaResponse cinemaResponse = new CinemaResponse(
                1L, "Rạp A", "rap-a", "Hà Nội", "1 Phố Huế", "0281234567", new BigDecimal("21.0285000"),
                new BigDecimal("105.8542000"), CinemaStatus.ACTIVE, List.of(facilityResponse), 3, false);
        when(cinemaPublicService.findPublicCinemas("Hà Nội")).thenReturn(List.of(cinemaResponse));

        mockMvc.perform(get("/api/v1/cinemas/public").param("city", "Hà Nội"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("rap-a"))
                .andExpect(jsonPath("$.data[0].phone").value("0281234567"))
                .andExpect(jsonPath("$.data[0].latitude").value(21.0285))
                .andExpect(jsonPath("$.data[0].facilities[0].name").value("Bãi đỗ xe ô tô"))
                .andExpect(jsonPath("$.data[0].hallCount").value(3));
    }

    @Test
    void citiesEndpointIsNotShadowedBySlugEndpoint() throws Exception {
        when(cinemaPublicService.findPublicCities()).thenReturn(List.of("Hà Nội", "Đà Nẵng"));

        mockMvc.perform(get("/api/v1/cinemas/public/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1]").value("Đà Nẵng"));
    }

    @Test
    void detailIncludesHalls() throws Exception {
        HallResponse hallResponse = new HallResponse(9L, 1L, "Phòng 01", ScreenType.SCREEN_4DX, 120, HallStatus.ACTIVE, false);
        CinemaDetailResponse cinemaDetailResponse = new CinemaDetailResponse(
                1L, "Rạp A", "rap-a", "Hà Nội", "1 Phố Huế", null, null, null, CinemaStatus.ACTIVE, List.of(),
                List.of(hallResponse), false);
        when(cinemaPublicService.findPublicCinemaBySlug("rap-a")).thenReturn(cinemaDetailResponse);

        mockMvc.perform(get("/api/v1/cinemas/public/rap-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.halls[0].screenType").value("4DX"))
                .andExpect(jsonPath("$.data.halls[0].totalSeats").value(120));
    }

    @Test
    void unknownSlugIs404() throws Exception {
        when(cinemaPublicService.findPublicCinemaBySlug("khong-co"))
                .thenThrow(new AppException("Không tìm thấy cụm rạp", HttpStatus.NOT_FOUND, "CINEMA_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/cinemas/public/khong-co"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CINEMA_NOT_FOUND"));
    }
}
