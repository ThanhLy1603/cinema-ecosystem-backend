package com.cinema.modules.fnb.service;

import com.cinema.modules.fnb.dto.response.FnbMenuResponse;
import com.cinema.modules.fnb.entity.FnbCombo;
import com.cinema.modules.fnb.entity.FnbItem;
import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;
import com.cinema.modules.fnb.repository.FnbComboRepository;
import com.cinema.modules.fnb.repository.FnbItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FnbPublicServiceTest {

    @Mock
    private FnbComboRepository fnbComboRepository;

    @Mock
    private FnbItemRepository fnbItemRepository;

    private FnbItem item(Long itemId, FnbStatus status) {
        FnbItem fnbItem = new FnbItem();
        fnbItem.setId(itemId);
        fnbItem.setName("Món " + itemId);
        fnbItem.setCategory(FnbCategory.DRINK);
        fnbItem.setPrice(new BigDecimal("30000"));
        fnbItem.setStatus(status);
        return fnbItem;
    }

    private FnbCombo combo(Long comboId, FnbItem fnbItem) {
        FnbCombo fnbCombo = new FnbCombo();
        fnbCombo.setId(comboId);
        fnbCombo.setName("Combo " + comboId);
        fnbCombo.setPrice(new BigDecimal("50000"));
        fnbCombo.replaceItems(Map.of(fnbItem.getId(), fnbItem), Map.of(fnbItem.getId(), 1));
        return fnbCombo;
    }

    @Test
    void menuHidesCombosWhoseItemsAreUnavailable() {
        FnbItem availableItem = item(1L, FnbStatus.AVAILABLE);
        FnbItem soldOutItem = item(2L, FnbStatus.UNAVAILABLE);
        when(fnbComboRepository.findAllByDeletedFalseAndStatusOrderByNameAscIdAsc(FnbStatus.AVAILABLE))
                .thenReturn(List.of(combo(10L, availableItem), combo(11L, soldOutItem)));
        when(fnbItemRepository.findAllByDeletedFalseAndStatusOrderByCategoryAscNameAscIdAsc(FnbStatus.AVAILABLE))
                .thenReturn(List.of(availableItem));
        FnbPublicService fnbPublicService = new FnbPublicService(fnbComboRepository, fnbItemRepository);

        FnbMenuResponse fnbMenuResponse = fnbPublicService.getMenu();

        assertEquals(1, fnbMenuResponse.combos().size());
        assertEquals(10L, fnbMenuResponse.combos().get(0).id());
        assertEquals(1, fnbMenuResponse.items().size());
    }
}
