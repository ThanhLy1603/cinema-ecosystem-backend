package com.cinema.modules.fnb.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.fnb.dto.request.FnbItemRequest;
import com.cinema.modules.fnb.dto.response.FnbItemResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FnbItemAdminServiceTest {

    private static final Long ITEM_ID = 4L;
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    @Mock
    private FnbItemRepository fnbItemRepository;

    @Mock
    private FnbComboRepository fnbComboRepository;

    private FnbItemAdminService fnbItemAdminService;

    @BeforeEach
    void setUp() {
        fnbItemAdminService =
                new FnbItemAdminService(fnbItemRepository, fnbComboRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private FnbItemRequest request(String name) {
        return new FnbItemRequest(name, FnbCategory.POPCORN, new BigDecimal("55000"), " ", FnbStatus.AVAILABLE);
    }

    private FnbItem existingItem(boolean deleted) {
        FnbItem fnbItem = new FnbItem();
        fnbItem.setId(ITEM_ID);
        fnbItem.setName("Bắp ngọt");
        fnbItem.setCategory(FnbCategory.POPCORN);
        fnbItem.setPrice(new BigDecimal("45000"));
        if (deleted) {
            fnbItem.markDeleted(NOW);
        }
        return fnbItem;
    }

    private void stubSave() {
        when(fnbItemRepository.saveAndFlush(any(FnbItem.class))).thenAnswer(invocation -> {
            FnbItem fnbItem = invocation.getArgument(0);
            if (fnbItem.getId() == null) {
                fnbItem.setId(ITEM_ID);
            }
            return fnbItem;
        });
    }

    @Test
    void createTrimsNameAndTurnsBlankImageIntoNull() {
        stubSave();

        FnbItemResponse fnbItemResponse = fnbItemAdminService.createItem(request("  Bắp mặn  "));

        assertEquals("Bắp mặn", fnbItemResponse.name());
        assertNull(fnbItemResponse.imageUrl());
        assertEquals(FnbCategory.POPCORN, fnbItemResponse.category());
        assertFalse(fnbItemResponse.deleted());
    }

    @Test
    void createRejectsDuplicateActiveName() {
        when(fnbItemRepository.existsByNameIgnoreCaseAndDeletedFalse("Bắp mặn")).thenReturn(true);

        AppException appException =
                assertThrows(AppException.class, () -> fnbItemAdminService.createItem(request("Bắp mặn")));

        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        assertEquals("FNB_NAME_EXISTS", appException.getErrorCode());
        verify(fnbItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void createMapsRaceOnUniqueIndexToConflict() {
        when(fnbItemRepository.saveAndFlush(any(FnbItem.class))).thenThrow(new DataIntegrityViolationException("dup"));

        AppException appException =
                assertThrows(AppException.class, () -> fnbItemAdminService.createItem(request("Bắp mặn")));

        assertEquals("FNB_NAME_EXISTS", appException.getErrorCode());
    }

    @Test
    void updateChangesFieldsOfActiveItem() {
        FnbItem fnbItem = existingItem(false);
        when(fnbItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(fnbItem));
        stubSave();

        FnbItemResponse fnbItemResponse = fnbItemAdminService.updateItem(ITEM_ID, request("Bắp phô mai"));

        assertEquals("Bắp phô mai", fnbItemResponse.name());
        assertEquals(new BigDecimal("55000"), fnbItemResponse.price());
    }

    @Test
    void updateRejectsNameOfAnotherItem() {
        when(fnbItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(existingItem(false)));
        when(fnbItemRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot("Bắp mặn", ITEM_ID)).thenReturn(true);

        AppException appException =
                assertThrows(AppException.class, () -> fnbItemAdminService.updateItem(ITEM_ID, request("Bắp mặn")));

        assertEquals("FNB_NAME_EXISTS", appException.getErrorCode());
    }

    @Test
    void updateTreatsDeletedItemAsNotFound() {
        when(fnbItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(existingItem(true)));

        AppException appException =
                assertThrows(AppException.class, () -> fnbItemAdminService.updateItem(ITEM_ID, request("Bắp")));

        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
        assertEquals("FNB_ITEM_NOT_FOUND", appException.getErrorCode());
    }

    @Test
    void deleteIsBlockedWhileActiveComboUsesTheItem() {
        FnbItem fnbItem = existingItem(false);
        when(fnbItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(fnbItem));
        when(fnbComboRepository.countActiveCombosUsingItem(ITEM_ID)).thenReturn(2L);

        AppException appException = assertThrows(AppException.class, () -> fnbItemAdminService.deleteItem(ITEM_ID));

        assertEquals("FNB_ITEM_IN_COMBO", appException.getErrorCode());
        assertFalse(fnbItem.isDeleted());
        verify(fnbItemRepository, never()).save(any());
    }

    @Test
    void deleteMarksItemAsSoftDeleted() {
        FnbItem fnbItem = existingItem(false);
        when(fnbItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(fnbItem));
        when(fnbComboRepository.countActiveCombosUsingItem(ITEM_ID)).thenReturn(0L);

        fnbItemAdminService.deleteItem(ITEM_ID);

        assertTrue(fnbItem.isDeleted());
        assertEquals(NOW, fnbItem.getDeletedAt());
        verify(fnbItemRepository).save(fnbItem);
    }

    @Test
    void restoreClearsDeletedFlag() {
        FnbItem fnbItem = existingItem(true);
        when(fnbItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(fnbItem));
        stubSave();

        FnbItemResponse fnbItemResponse = fnbItemAdminService.restoreItem(ITEM_ID);

        assertFalse(fnbItemResponse.deleted());
    }

    @Test
    void restoreIsBlockedWhenNameWasTakenMeanwhile() {
        FnbItem fnbItem = existingItem(true);
        when(fnbItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(fnbItem));
        when(fnbItemRepository.existsByNameIgnoreCaseAndDeletedFalse("Bắp ngọt")).thenReturn(true);

        AppException appException = assertThrows(AppException.class, () -> fnbItemAdminService.restoreItem(ITEM_ID));

        assertEquals("FNB_NAME_EXISTS", appException.getErrorCode());
        assertTrue(fnbItem.isDeleted());
    }

    @Test
    void restoreOfActiveItemIsNotFound() {
        when(fnbItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(existingItem(false)));

        AppException appException = assertThrows(AppException.class, () -> fnbItemAdminService.restoreItem(ITEM_ID));

        assertEquals("FNB_ITEM_NOT_FOUND", appException.getErrorCode());
    }
}
