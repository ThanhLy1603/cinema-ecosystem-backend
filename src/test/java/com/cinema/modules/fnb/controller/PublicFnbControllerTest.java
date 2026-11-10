package com.cinema.modules.fnb.controller;

import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.fnb.dto.response.FnbItemResponse;
import com.cinema.modules.fnb.dto.response.FnbMenuResponse;
import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;
import com.cinema.modules.fnb.service.interfaces.IFnbPublicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicFnbControllerTest {

    @Mock
    private IFnbPublicService fnbPublicService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicFnbController(fnbPublicService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void menuReturnsCombosAndItems() throws Exception {
        when(fnbPublicService.getMenu()).thenReturn(new FnbMenuResponse(
                List.of(),
                List.of(new FnbItemResponse(1L, "Pepsi", FnbCategory.DRINK, new BigDecimal("30000"), null,
                        FnbStatus.AVAILABLE, false))));

        mockMvc.perform(get("/api/v1/fnb/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("Pepsi"))
                .andExpect(jsonPath("$.data.combos").isEmpty());
    }
}
