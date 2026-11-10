package com.cinema.modules.showtime.controller;

import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.showtime.dto.request.PriceRuleRequest;
import com.cinema.modules.showtime.service.interfaces.IDynamicPricingService;
import com.cinema.modules.showtime.service.interfaces.IPriceRuleAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminPriceRuleControllerTest {

    private static final String ADMIN_ONLY = "hasAuthority('ROLE_ADMIN')";
    private static final ObjectMapper OBJECT_MAPPER =
            Jackson2ObjectMapperBuilder.json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Mock
    private IPriceRuleAdminService priceRuleAdminService;

    @Mock
    private IDynamicPricingService dynamicPricingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminPriceRuleController(priceRuleAdminService, dynamicPricingService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(OBJECT_MAPPER))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PreAuthorize methodAuthorization(String methodName) {
        Method method = Arrays.stream(AdminPriceRuleController.class.getDeclaredMethods())
                .filter(declaredMethod -> declaredMethod.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return method.getAnnotation(PreAuthorize.class);
    }

    @Test
    void onlyAdminsMayChangeRulesWhileManagersCanReadAndSimulate() {
        assertEquals(ADMIN_ONLY, methodAuthorization("createPriceRule").value());
        assertEquals(ADMIN_ONLY, methodAuthorization("updatePriceRule").value());
        assertEquals(ADMIN_ONLY, methodAuthorization("deletePriceRule").value());
        assertEquals(ADMIN_ONLY, methodAuthorization("restorePriceRule").value());
        assertNull(methodAuthorization("simulatePrices"));
        assertEquals(
                "hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')",
                AdminPriceRuleController.class.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void deletedRulesAreVisibleToAdminsOnly() {
        assertEquals(
                "hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER') and (hasAuthority('ROLE_ADMIN') or !#deleted)",
                methodAuthorization("listPriceRules").value());
    }

    @Test
    void listDefaultsToActiveRules() throws Exception {
        when(priceRuleAdminService.listPriceRules(false)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/price-rules")).andExpect(status().isOk());

        verify(priceRuleAdminService).listPriceRules(false);
    }

    @Test
    void createRejectsMissingNameTypeAndNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/v1/admin/price-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruleName":" ","surchargeAmount":-5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.ruleName").exists())
                .andExpect(jsonPath("$.data.ruleType").exists())
                .andExpect(jsonPath("$.data.surchargeAmount").exists());

        verifyNoInteractions(priceRuleAdminService);
    }

    @Test
    void createAcceptsTimeSlotRuleWithHourMinuteBounds() throws Exception {
        mockMvc.perform(post("/api/v1/admin/price-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruleName":"Suất đêm","ruleType":"TIME_SLOT","surchargeAmount":10000,
                                 "timeFrom":"22:00","timeTo":"04:00"}
                                """))
                .andExpect(status().isCreated());

        verify(priceRuleAdminService).createPriceRule(any(PriceRuleRequest.class));
    }

    @Test
    void createRejectsUnknownScreenTypeAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/price-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruleName":"X","ruleType":"SCREEN_TYPE","surchargeAmount":1000,"screenType":"8K"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(priceRuleAdminService);
    }

    @Test
    void simulateRequiresAllInputs() throws Exception {
        mockMvc.perform(post("/api/v1/admin/price-rules/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"basePrice":90000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.screenType").exists())
                .andExpect(jsonPath("$.data.startTime").exists());

        verifyNoInteractions(dynamicPricingService);
    }
}
