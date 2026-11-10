package com.cinema.modules.fnb.controller;

import com.cinema.common.exception.AppException;
import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.fnb.dto.request.FnbComboRequest;
import com.cinema.modules.fnb.dto.request.FnbItemRequest;
import com.cinema.modules.fnb.dto.response.FnbComboItemResponse;
import com.cinema.modules.fnb.dto.response.FnbComboResponse;
import com.cinema.modules.fnb.dto.response.FnbItemResponse;
import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;
import com.cinema.modules.fnb.service.interfaces.IFnbComboAdminService;
import com.cinema.modules.fnb.service.interfaces.IFnbItemAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminFnbControllerTest {

    @Mock
    private IFnbItemAdminService fnbItemAdminService;

    @Mock
    private IFnbComboAdminService fnbComboAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminFnbItemController(fnbItemAdminService), new AdminFnbComboController(fnbComboAdminService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private FnbItemResponse itemResponse() {
        return new FnbItemResponse(1L, "Bắp ngọt", FnbCategory.POPCORN, new BigDecimal("45000"), null, FnbStatus.AVAILABLE, false);
    }

    @Test
    void createItemReturns201() throws Exception {
        when(fnbItemAdminService.createItem(any(FnbItemRequest.class))).thenReturn(itemResponse());

        mockMvc.perform(post("/api/v1/admin/fnb/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bắp ngọt","category":"POPCORN","price":45000,"status":"AVAILABLE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category").value("POPCORN"))
                .andExpect(jsonPath("$.data.price").value(45000));
    }

    @Test
    void createItemRejectsInvalidPayloadWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/admin/fnb/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","category":"POPCORN","price":-1,"status":"AVAILABLE","imageUrl":"http://x/p.jpg"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.name").exists())
                .andExpect(jsonPath("$.data.price").exists())
                .andExpect(jsonPath("$.data.imageUrl").exists());

        verify(fnbItemAdminService, never()).createItem(any());
    }

    @Test
    void createItemRejectsPriceAboveLimitAndMissingCategory() throws Exception {
        mockMvc.perform(post("/api/v1/admin/fnb/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bắp","price":10000001,"status":"AVAILABLE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.price").exists())
                .andExpect(jsonPath("$.data.category").exists());
    }

    @Test
    void createItemReportsDuplicateNameAsConflict() throws Exception {
        when(fnbItemAdminService.createItem(any(FnbItemRequest.class)))
                .thenThrow(new AppException("Tên món đã tồn tại", HttpStatus.CONFLICT, "FNB_NAME_EXISTS"));

        mockMvc.perform(post("/api/v1/admin/fnb/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bắp ngọt","category":"POPCORN","price":45000,"status":"AVAILABLE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FNB_NAME_EXISTS"));
    }

    @Test
    void searchItemsPassesFilters() throws Exception {
        when(fnbItemAdminService.searchItems("bắp", FnbCategory.POPCORN, FnbStatus.AVAILABLE, false, 0, 20))
                .thenReturn(new PageResponse<>(List.of(itemResponse()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/admin/fnb/items")
                        .param("keyword", "bắp")
                        .param("category", "POPCORN")
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Bắp ngọt"));
    }

    @Test
    void searchItemsRejectsUnknownCategory() throws Exception {
        mockMvc.perform(get("/api/v1/admin/fnb/items").param("category", "PIZZA")).andExpect(status().isBadRequest());
    }

    @Test
    void createComboRequiresAtLeastOneItem() throws Exception {
        mockMvc.perform(post("/api/v1/admin/fnb/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Combo","price":50000,"status":"AVAILABLE","items":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.items").exists());

        verify(fnbComboAdminService, never()).createCombo(any());
    }

    @Test
    void createComboRejectsOutOfRangeQuantityAndNullLine() throws Exception {
        mockMvc.perform(post("/api/v1/admin/fnb/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Combo","price":50000,"status":"AVAILABLE","items":[{"itemId":1,"quantity":0}, null]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(fnbComboAdminService, never()).createCombo(any());
    }

    @Test
    void createComboReturns201WithComputedTotals() throws Exception {
        FnbComboResponse fnbComboResponse = new FnbComboResponse(
                3L, "Combo đôi", null, new BigDecimal("99000"), null, FnbStatus.AVAILABLE,
                List.of(new FnbComboItemResponse(1L, "Bắp ngọt", FnbCategory.POPCORN, new BigDecimal("45000"), 1,
                        FnbStatus.AVAILABLE, false)),
                new BigDecimal("45000"), true, false);
        when(fnbComboAdminService.createCombo(any(FnbComboRequest.class))).thenReturn(fnbComboResponse);

        mockMvc.perform(post("/api/v1/admin/fnb/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Combo đôi","price":99000,"status":"AVAILABLE","items":[{"itemId":1,"quantity":1}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.itemsTotalPrice").value(45000))
                .andExpect(jsonPath("$.data.orderable").value(true))
                .andExpect(jsonPath("$.data.items[0].itemName").value("Bắp ngọt"));
    }
}
