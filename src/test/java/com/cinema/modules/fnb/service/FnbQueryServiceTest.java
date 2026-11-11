package com.cinema.modules.fnb.service;

import com.cinema.modules.fnb.dto.response.FnbProductInfoResponse;
import com.cinema.modules.fnb.entity.FnbCombo;
import com.cinema.modules.fnb.entity.FnbComboItem;
import com.cinema.modules.fnb.entity.FnbItem;
import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;
import com.cinema.modules.fnb.repository.FnbComboRepository;
import com.cinema.modules.fnb.repository.FnbItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FnbQueryServiceTest {

    @Mock
    private FnbComboRepository fnbComboRepository;

    @Mock
    private FnbItemRepository fnbItemRepository;

    @InjectMocks
    private FnbQueryService fnbQueryService;

    @Test
    void itemInfoIsOrderableOnlyWhenAvailableAndNotDeleted() {
        FnbItem availableFnbItem = fnbItem(1L, FnbStatus.AVAILABLE, false);
        FnbItem unavailableFnbItem = fnbItem(2L, FnbStatus.UNAVAILABLE, false);
        FnbItem deletedFnbItem = fnbItem(3L, FnbStatus.AVAILABLE, true);
        when(fnbItemRepository.findAllByIdIn(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(availableFnbItem, unavailableFnbItem, deletedFnbItem));

        Map<Long, FnbProductInfoResponse> itemInfos = fnbQueryService.findItemInfos(List.of(1L, 2L, 3L));

        assertTrue(itemInfos.get(1L).orderable());
        assertFalse(itemInfos.get(2L).orderable());
        assertFalse(itemInfos.get(3L).orderable());
        assertEquals(new BigDecimal("15000"), itemInfos.get(1L).price());
    }

    @Test
    void comboInfoIsNotOrderableWhenAComponentItemIsUnavailable() {
        FnbItem unavailableFnbItem = fnbItem(2L, FnbStatus.UNAVAILABLE, false);
        FnbCombo fnbCombo = fnbCombo(10L, FnbStatus.AVAILABLE);
        fnbCombo.getFnbComboItems().add(new FnbComboItem(fnbCombo, unavailableFnbItem, 1));
        FnbCombo healthyFnbCombo = fnbCombo(11L, FnbStatus.AVAILABLE);
        healthyFnbCombo.getFnbComboItems().add(new FnbComboItem(healthyFnbCombo, fnbItem(1L, FnbStatus.AVAILABLE, false), 2));
        when(fnbComboRepository.findWithItemsByIdIn(List.of(10L, 11L))).thenReturn(List.of(fnbCombo, healthyFnbCombo));

        Map<Long, FnbProductInfoResponse> comboInfos = fnbQueryService.findComboInfos(List.of(10L, 11L));

        assertFalse(comboInfos.get(10L).orderable());
        assertTrue(comboInfos.get(11L).orderable());
        assertEquals(new BigDecimal("99000"), comboInfos.get(11L).price());
    }

    @Test
    void emptyIdsReturnEmptyMapsWithoutQueryingTheDatabase() {
        assertTrue(fnbQueryService.findComboInfos(List.of()).isEmpty());
        assertTrue(fnbQueryService.findItemInfos(List.of()).isEmpty());

        verifyNoInteractions(fnbComboRepository, fnbItemRepository);
    }

    private FnbItem fnbItem(Long id, FnbStatus status, boolean deleted) {
        FnbItem fnbItem = new FnbItem();
        fnbItem.setId(id);
        fnbItem.setName("Món " + id);
        fnbItem.setCategory(FnbCategory.DRINK);
        fnbItem.setPrice(new BigDecimal("15000"));
        fnbItem.setStatus(status);
        if (deleted) {
            fnbItem.markDeleted(Instant.parse("2026-11-01T00:00:00Z"));
        }
        return fnbItem;
    }

    private FnbCombo fnbCombo(Long id, FnbStatus status) {
        FnbCombo fnbCombo = new FnbCombo();
        fnbCombo.setId(id);
        fnbCombo.setName("Combo " + id);
        fnbCombo.setPrice(new BigDecimal("99000"));
        fnbCombo.setStatus(status);
        return fnbCombo;
    }
}
