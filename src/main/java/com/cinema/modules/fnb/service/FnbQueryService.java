package com.cinema.modules.fnb.service;

import com.cinema.modules.fnb.dto.response.FnbComboItemResponse;
import com.cinema.modules.fnb.dto.response.FnbComboResponse;
import com.cinema.modules.fnb.dto.response.FnbProductInfoResponse;
import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;
import com.cinema.modules.fnb.repository.FnbComboRepository;
import com.cinema.modules.fnb.repository.FnbItemRepository;
import com.cinema.modules.fnb.service.interfaces.IFnbQueryService;
import com.cinema.modules.fnb.util.FnbMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FnbQueryService implements IFnbQueryService {

    private static final Map<FnbCategory, String> COMPOSITION_LABELS = Map.of(
            FnbCategory.POPCORN, "bắp",
            FnbCategory.DRINK, "nước",
            FnbCategory.SNACK, "đồ ăn nhẹ");

    private final FnbComboRepository fnbComboRepository;
    private final FnbItemRepository fnbItemRepository;

    public FnbQueryService(FnbComboRepository fnbComboRepository, FnbItemRepository fnbItemRepository) {
        this.fnbComboRepository = fnbComboRepository;
        this.fnbItemRepository = fnbItemRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Map<Long, FnbProductInfoResponse> findComboInfos(Collection<Long> comboIds) {
        if (comboIds.isEmpty()) {
            return Map.of();
        }
        return fnbComboRepository.findWithItemsByIdIn(comboIds).stream()
                .map(FnbMapper::toFnbComboResponse)
                .collect(Collectors.toMap(FnbComboResponse::id, this::toProductInfo));
    }

    @Transactional(readOnly = true)
    @Override
    public Map<Long, FnbProductInfoResponse> findItemInfos(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return fnbItemRepository.findAllByIdIn(itemIds).stream()
                .collect(Collectors.toMap(
                        fnbItem -> fnbItem.getId(),
                        fnbItem -> new FnbProductInfoResponse(
                                fnbItem.getId(),
                                fnbItem.getName(),
                                fnbItem.getImageUrl(),
                                fnbItem.getPrice(),
                                !fnbItem.isDeleted() && fnbItem.getStatus() == FnbStatus.AVAILABLE)));
    }

    @Transactional(readOnly = true)
    @Override
    public Map<Long, String> findComboCompositions(Collection<Long> comboIds) {
        if (comboIds.isEmpty()) {
            return Map.of();
        }
        return fnbComboRepository.findWithItemsByIdIn(comboIds).stream()
                .map(FnbMapper::toFnbComboResponse)
                .collect(Collectors.toMap(FnbComboResponse::id, this::toCompositionLabel));
    }

    private String toCompositionLabel(FnbComboResponse fnbComboResponse) {
        Map<FnbCategory, Integer> quantitiesByCategory = new EnumMap<>(FnbCategory.class);
        for (FnbComboItemResponse fnbComboItemResponse : fnbComboResponse.items()) {
            quantitiesByCategory.merge(fnbComboItemResponse.category(), fnbComboItemResponse.quantity(), Integer::sum);
        }
        return quantitiesByCategory.entrySet().stream()
                .map(entry -> entry.getValue() + " " + COMPOSITION_LABELS.get(entry.getKey()))
                .collect(Collectors.joining(" + "));
    }

    private FnbProductInfoResponse toProductInfo(FnbComboResponse fnbComboResponse) {
        return new FnbProductInfoResponse(
                fnbComboResponse.id(),
                fnbComboResponse.name(),
                fnbComboResponse.imageUrl(),
                fnbComboResponse.price(),
                fnbComboResponse.orderable());
    }
}
