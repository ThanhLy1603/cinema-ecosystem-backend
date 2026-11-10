package com.cinema.modules.showtime.controller;

import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.showtime.dto.response.SeatTypePriceResponse;
import com.cinema.modules.showtime.dto.response.ShowtimePriceResponse;
import com.cinema.modules.showtime.service.interfaces.IDynamicPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicShowtimePriceControllerTest {

    @Mock
    private IDynamicPricingService dynamicPricingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicShowtimePriceController(dynamicPricingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsBreakdownAndPerSeatTypePrices() throws Exception {
        when(dynamicPricingService.getShowtimePrices(70L)).thenReturn(new ShowtimePriceResponse(
                70L, ScreenType.SCREEN_2D, Instant.parse("2026-11-14T12:00:00Z"), new BigDecimal("90000"),
                BigDecimal.ZERO, new BigDecimal("15000"), BigDecimal.ZERO, List.of(),
                List.of(new SeatTypePriceResponse("VIP", "Ghế VIP", new BigDecimal("20000"), new BigDecimal("125000")))));

        mockMvc.perform(get("/api/v1/showtimes/public/70/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.basePrice").value(90000))
                .andExpect(jsonPath("$.data.dayTypeSurcharge").value(15000))
                .andExpect(jsonPath("$.data.seatTypePrices[0].seatTypeCode").value("VIP"))
                .andExpect(jsonPath("$.data.seatTypePrices[0].totalPrice").value(125000));
    }

    @Test
    void rejectsNonNumericShowtimeId() throws Exception {
        mockMvc.perform(get("/api/v1/showtimes/public/abc/prices")).andExpect(status().isBadRequest());
    }
}
