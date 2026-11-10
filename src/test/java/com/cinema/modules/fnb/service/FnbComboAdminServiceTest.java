package com.cinema.modules.fnb.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.fnb.dto.request.FnbComboItemRequest;
import com.cinema.modules.fnb.dto.request.FnbComboRequest;
import com.cinema.modules.fnb.dto.response.FnbComboResponse;
import com.cinema.modules.fnb.entity.FnbCombo;
import com.cinema.modules.fnb.entity.FnbItem;
import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;
import com.cinema.modules.fnb.repository.FnbComboRepository;
import com.cinema.modules.fnb.repository.FnbItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FnbComboAdminServiceTest {

    private static final Long COMBO_ID = 9L;
    private static final Long POPCORN_ID = 1L;
    private static final Long DRINK_ID = 2L;
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    @Mock
    private FnbComboRepository fnbComboRepository;

    @Mock
    private FnbItemRepository fnbItemRepository;

    private FnbComboAdminService fnbComboAdminService;
    private FnbItem popcornItem;
    private FnbItem drinkItem;

    @BeforeEach
    void setUp() {
        fnbComboAdminService =
                new FnbComboAdminService(fnbComboRepository, fnbItemRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        popcornItem = item(POPCORN_ID, "Bắp lớn", FnbCategory.POPCORN, "60000");
        drinkItem = item(DRINK_ID, "Pepsi", FnbCategory.DRINK, "30000");
    }

    private FnbItem item(Long itemId, String name, FnbCategory category, String price) {
        FnbItem fnbItem = new FnbItem();
        fnbItem.setId(itemId);
        fnbItem.setName(name);
        fnbItem.setCategory(category);
        fnbItem.setPrice(new BigDecimal(price));
        return fnbItem;
    }

    private FnbComboRequest request(String name, List<FnbComboItemRequest> items) {
        return new FnbComboRequest(name, " Combo cho hai người ", new BigDecimal("99000"), "", FnbStatus.AVAILABLE, items);
    }

    private List<FnbComboItemRequest> popcornAndTwoDrinks() {
        return List.of(new FnbComboItemRequest(POPCORN_ID, 1), new FnbComboItemRequest(DRINK_ID, 2));
    }

    private void stubSave() {
        when(fnbComboRepository.saveAndFlush(any(FnbCombo.class))).thenAnswer(invocation -> {
            FnbCombo fnbCombo = invocation.getArgument(0);
            if (fnbCombo.getId() == null) {
                fnbCombo.setId(COMBO_ID);
            }
            return fnbCombo;
        });
    }

    private FnbCombo existingCombo(boolean deleted) {
        FnbCombo fnbCombo = new FnbCombo();
        fnbCombo.setId(COMBO_ID);
        fnbCombo.setName("Combo đôi");
        fnbCombo.setPrice(new BigDecimal("99000"));
        fnbCombo.replaceItems(
                java.util.Map.of(POPCORN_ID, popcornItem), java.util.Map.of(POPCORN_ID, 1));
        if (deleted) {
            fnbCombo.markDeleted(NOW);
        }
        return fnbCombo;
    }

    @Test
    void createStoresItemsAndComputesTotals() {
        stubSave();
        when(fnbItemRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(popcornItem, drinkItem));

        FnbComboResponse fnbComboResponse = fnbComboAdminService.createCombo(request("  Combo đôi  ", popcornAndTwoDrinks()));

        assertEquals("Combo đôi", fnbComboResponse.name());
        assertEquals("Combo cho hai người", fnbComboResponse.description());
        assertEquals(2, fnbComboResponse.items().size());
        assertEquals(new BigDecimal("120000"), fnbComboResponse.itemsTotalPrice());
        assertTrue(fnbComboResponse.orderable());
    }

    @Test
    void createRejectsDuplicateComboName() {
        when(fnbComboRepository.existsByNameIgnoreCaseAndDeletedFalse("Combo đôi")).thenReturn(true);

        AppException appException = assertThrows(
                AppException.class, () -> fnbComboAdminService.createCombo(request("Combo đôi", popcornAndTwoDrinks())));

        assertEquals("FNB_NAME_EXISTS", appException.getErrorCode());
        verify(fnbComboRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsSameItemTwice() {
        List<FnbComboItemRequest> duplicateItems =
                List.of(new FnbComboItemRequest(POPCORN_ID, 1), new FnbComboItemRequest(POPCORN_ID, 2));

        AppException appException = assertThrows(
                AppException.class, () -> fnbComboAdminService.createCombo(request("Combo", duplicateItems)));

        assertEquals("DUPLICATE_COMBO_ITEM", appException.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, appException.getStatus());
    }

    @Test
    void createRejectsUnknownItem() {
        when(fnbItemRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(popcornItem));

        AppException appException = assertThrows(
                AppException.class, () -> fnbComboAdminService.createCombo(request("Combo", popcornAndTwoDrinks())));

        assertEquals("INVALID_FNB_ITEM", appException.getErrorCode());
        verify(fnbComboRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsDeletedItem() {
        drinkItem.markDeleted(NOW);
        when(fnbItemRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(popcornItem, drinkItem));

        AppException appException = assertThrows(
                AppException.class, () -> fnbComboAdminService.createCombo(request("Combo", popcornAndTwoDrinks())));

        assertEquals("INVALID_FNB_ITEM", appException.getErrorCode());
    }

    @Test
    void comboWithUnavailableItemIsNotOrderable() {
        drinkItem.setStatus(FnbStatus.UNAVAILABLE);
        stubSave();
        when(fnbItemRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(popcornItem, drinkItem));

        FnbComboResponse fnbComboResponse = fnbComboAdminService.createCombo(request("Combo", popcornAndTwoDrinks()));

        assertFalse(fnbComboResponse.orderable());
    }

    @Test
    void updateKeepsExistingLinesAndChangesQuantitiesAndRemovesMissing() {
        FnbCombo fnbCombo = existingCombo(false);
        fnbCombo.replaceItems(
                java.util.Map.of(POPCORN_ID, popcornItem, DRINK_ID, drinkItem),
                java.util.Map.of(POPCORN_ID, 1, DRINK_ID, 1));
        when(fnbComboRepository.findWithItemsById(COMBO_ID)).thenReturn(Optional.of(fnbCombo));
        when(fnbItemRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(drinkItem));
        stubSave();

        FnbComboResponse fnbComboResponse = fnbComboAdminService.updateCombo(
                COMBO_ID, request("Combo đôi", List.of(new FnbComboItemRequest(DRINK_ID, 3))));

        assertEquals(1, fnbComboResponse.items().size());
        assertEquals(DRINK_ID, fnbComboResponse.items().get(0).itemId());
        assertEquals(3, fnbComboResponse.items().get(0).quantity());
    }

    @Test
    void updateTreatsDeletedComboAsNotFound() {
        when(fnbComboRepository.findWithItemsById(COMBO_ID)).thenReturn(Optional.of(existingCombo(true)));

        AppException appException = assertThrows(AppException.class,
                () -> fnbComboAdminService.updateCombo(COMBO_ID, request("Combo", popcornAndTwoDrinks())));

        assertEquals("FNB_COMBO_NOT_FOUND", appException.getErrorCode());
    }

    @Test
    void deleteMarksComboAsSoftDeleted() {
        FnbCombo fnbCombo = existingCombo(false);
        when(fnbComboRepository.findWithItemsById(COMBO_ID)).thenReturn(Optional.of(fnbCombo));

        fnbComboAdminService.deleteCombo(COMBO_ID);

        assertTrue(fnbCombo.isDeleted());
        verify(fnbComboRepository).save(fnbCombo);
    }

    @Test
    void restoreIsBlockedWhenComboContainsDeletedItem() {
        FnbCombo fnbCombo = existingCombo(true);
        popcornItem.markDeleted(NOW);
        when(fnbComboRepository.findWithItemsById(COMBO_ID)).thenReturn(Optional.of(fnbCombo));

        AppException appException = assertThrows(AppException.class, () -> fnbComboAdminService.restoreCombo(COMBO_ID));

        assertEquals("FNB_COMBO_HAS_DELETED_ITEM", appException.getErrorCode());
        assertTrue(fnbCombo.isDeleted());
    }

    @Test
    void restoreIsBlockedWhenNameWasTakenMeanwhile() {
        when(fnbComboRepository.findWithItemsById(COMBO_ID)).thenReturn(Optional.of(existingCombo(true)));
        when(fnbComboRepository.existsByNameIgnoreCaseAndDeletedFalse("Combo đôi")).thenReturn(true);

        AppException appException = assertThrows(AppException.class, () -> fnbComboAdminService.restoreCombo(COMBO_ID));

        assertEquals("FNB_NAME_EXISTS", appException.getErrorCode());
    }

    @Test
    void restoreClearsDeletedFlag() {
        FnbCombo fnbCombo = existingCombo(true);
        when(fnbComboRepository.findWithItemsById(COMBO_ID)).thenReturn(Optional.of(fnbCombo));
        stubSave();

        FnbComboResponse fnbComboResponse = fnbComboAdminService.restoreCombo(COMBO_ID);

        assertFalse(fnbComboResponse.deleted());
    }
}
