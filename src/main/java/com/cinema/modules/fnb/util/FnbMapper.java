package com.cinema.modules.fnb.util;

import com.cinema.modules.fnb.dto.response.FnbComboItemResponse;
import com.cinema.modules.fnb.dto.response.FnbComboResponse;
import com.cinema.modules.fnb.dto.response.FnbItemResponse;
import com.cinema.modules.fnb.entity.FnbCombo;
import com.cinema.modules.fnb.entity.FnbComboItem;
import com.cinema.modules.fnb.entity.FnbItem;
import com.cinema.modules.fnb.enums.FnbStatus;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public final class FnbMapper {

    private FnbMapper() {
    }

    public static FnbItemResponse toFnbItemResponse(FnbItem fnbItem) {
        return new FnbItemResponse(
                fnbItem.getId(),
                fnbItem.getName(),
                fnbItem.getCategory(),
                fnbItem.getPrice(),
                fnbItem.getImageUrl(),
                fnbItem.getStatus(),
                fnbItem.isDeleted());
    }

    public static FnbComboItemResponse toFnbComboItemResponse(FnbComboItem fnbComboItem) {
        FnbItem fnbItem = fnbComboItem.getFnbItem();
        return new FnbComboItemResponse(
                fnbItem.getId(),
                fnbItem.getName(),
                fnbItem.getCategory(),
                fnbItem.getPrice(),
                fnbComboItem.getQuantity(),
                fnbItem.getStatus(),
                fnbItem.isDeleted());
    }

    public static FnbComboResponse toFnbComboResponse(FnbCombo fnbCombo) {
        List<FnbComboItemResponse> fnbComboItemResponses = fnbCombo.getFnbComboItems().stream()
                .map(FnbMapper::toFnbComboItemResponse)
                .sorted(Comparator.comparing(FnbComboItemResponse::category)
                        .thenComparing(FnbComboItemResponse::itemName)
                        .thenComparing(FnbComboItemResponse::itemId))
                .toList();
        BigDecimal itemsTotalPrice = fnbComboItemResponses.stream()
                .map(fnbComboItemResponse ->
                        fnbComboItemResponse.unitPrice().multiply(BigDecimal.valueOf(fnbComboItemResponse.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean allItemsOrderable = fnbComboItemResponses.stream()
                .allMatch(fnbComboItemResponse ->
                        fnbComboItemResponse.itemStatus() == FnbStatus.AVAILABLE && !fnbComboItemResponse.itemDeleted());
        boolean orderable = !fnbCombo.isDeleted()
                && fnbCombo.getStatus() == FnbStatus.AVAILABLE
                && !fnbComboItemResponses.isEmpty()
                && allItemsOrderable;
        return new FnbComboResponse(
                fnbCombo.getId(),
                fnbCombo.getName(),
                fnbCombo.getDescription(),
                fnbCombo.getPrice(),
                fnbCombo.getImageUrl(),
                fnbCombo.getStatus(),
                fnbComboItemResponses,
                itemsTotalPrice,
                orderable,
                fnbCombo.isDeleted());
    }
}
