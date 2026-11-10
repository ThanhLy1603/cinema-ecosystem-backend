package com.cinema.modules.fnb.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.fnb.dto.request.FnbComboItemRequest;
import com.cinema.modules.fnb.dto.request.FnbComboRequest;
import com.cinema.modules.fnb.dto.response.FnbComboResponse;
import com.cinema.modules.fnb.entity.FnbCombo;
import com.cinema.modules.fnb.entity.FnbComboItem;
import com.cinema.modules.fnb.entity.FnbItem;
import com.cinema.modules.fnb.enums.FnbStatus;
import com.cinema.modules.fnb.repository.FnbComboRepository;
import com.cinema.modules.fnb.repository.FnbItemRepository;
import com.cinema.modules.fnb.service.interfaces.IFnbComboAdminService;
import com.cinema.modules.fnb.specification.FnbSpecifications;
import com.cinema.modules.fnb.util.FnbMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FnbComboAdminService implements IFnbComboAdminService {

    static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final FnbComboRepository fnbComboRepository;
    private final FnbItemRepository fnbItemRepository;
    private final Clock clock;

    public FnbComboAdminService(
            FnbComboRepository fnbComboRepository, FnbItemRepository fnbItemRepository, Clock clock) {
        this.fnbComboRepository = fnbComboRepository;
        this.fnbItemRepository = fnbItemRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<FnbComboResponse> searchCombos(
            String keyword, FnbStatus status, boolean deleted, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE),
                Sort.by("name", "id"));
        Page<FnbCombo> fnbComboPage =
                fnbComboRepository.findAll(FnbSpecifications.searchCombos(keyword, status, deleted), pageable);
        List<Long> comboIds = fnbComboPage.getContent().stream().map(FnbCombo::getId).toList();
        Map<Long, FnbCombo> fnbCombosById = comboIds.isEmpty()
                ? Map.of()
                : fnbComboRepository.findWithItemsByIdIn(comboIds).stream()
                        .collect(Collectors.toMap(FnbCombo::getId, Function.identity()));
        List<FnbComboResponse> fnbComboResponses = comboIds.stream()
                .map(comboId -> FnbMapper.toFnbComboResponse(fnbCombosById.get(comboId)))
                .toList();
        return PageResponse.of(fnbComboPage, fnbComboResponses);
    }

    @Transactional
    @Override
    public FnbComboResponse createCombo(FnbComboRequest fnbComboRequest) {
        String name = fnbComboRequest.name().trim();
        if (fnbComboRepository.existsByNameIgnoreCaseAndDeletedFalse(name)) {
            throw nameAlreadyUsed();
        }
        Map<Long, Integer> quantitiesByItemId = toQuantitiesByItemId(fnbComboRequest.items());
        Map<Long, FnbItem> fnbItemsById = loadActiveItems(quantitiesByItemId.keySet());
        FnbCombo fnbCombo = new FnbCombo();
        applyRequest(fnbCombo, fnbComboRequest);
        FnbCombo savedFnbCombo = saveCombo(fnbCombo);
        savedFnbCombo.replaceItems(fnbItemsById, quantitiesByItemId);
        return FnbMapper.toFnbComboResponse(saveCombo(savedFnbCombo));
    }

    @Transactional
    @Override
    public FnbComboResponse updateCombo(Long comboId, FnbComboRequest fnbComboRequest) {
        FnbCombo fnbCombo = loadCombo(comboId, false);
        String name = fnbComboRequest.name().trim();
        if (fnbComboRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(name, comboId)) {
            throw nameAlreadyUsed();
        }
        Map<Long, Integer> quantitiesByItemId = toQuantitiesByItemId(fnbComboRequest.items());
        Map<Long, FnbItem> fnbItemsById = loadActiveItems(quantitiesByItemId.keySet());
        applyRequest(fnbCombo, fnbComboRequest);
        fnbCombo.replaceItems(fnbItemsById, quantitiesByItemId);
        return FnbMapper.toFnbComboResponse(saveCombo(fnbCombo));
    }

    @Transactional
    @Override
    public void deleteCombo(Long comboId) {
        FnbCombo fnbCombo = loadCombo(comboId, false);
        fnbCombo.markDeleted(Instant.now(clock));
        fnbComboRepository.save(fnbCombo);
    }

    @Transactional
    @Override
    public FnbComboResponse restoreCombo(Long comboId) {
        FnbCombo fnbCombo = loadCombo(comboId, true);
        if (fnbComboRepository.existsByNameIgnoreCaseAndDeletedFalse(fnbCombo.getName())) {
            throw nameAlreadyUsed();
        }
        for (FnbComboItem fnbComboItem : fnbCombo.getFnbComboItems()) {
            if (fnbComboItem.getFnbItem().isDeleted()) {
                throw new AppException(
                        "Combo chứa món đã bị xóa (" + fnbComboItem.getFnbItem().getName()
                                + "), hãy khôi phục món đó trước",
                        HttpStatus.CONFLICT,
                        "FNB_COMBO_HAS_DELETED_ITEM");
            }
        }
        fnbCombo.restore();
        return FnbMapper.toFnbComboResponse(saveCombo(fnbCombo));
    }

    private FnbCombo loadCombo(Long comboId, boolean deleted) {
        return fnbComboRepository.findWithItemsById(comboId)
                .filter(fnbCombo -> fnbCombo.isDeleted() == deleted)
                .orElseThrow(() -> new AppException("Không tìm thấy combo", HttpStatus.NOT_FOUND, "FNB_COMBO_NOT_FOUND"));
    }

    private Map<Long, Integer> toQuantitiesByItemId(List<FnbComboItemRequest> fnbComboItemRequests) {
        Map<Long, Integer> quantitiesByItemId = new LinkedHashMap<>();
        for (FnbComboItemRequest fnbComboItemRequest : fnbComboItemRequests) {
            if (quantitiesByItemId.put(fnbComboItemRequest.itemId(), fnbComboItemRequest.quantity()) != null) {
                throw new AppException(
                        "Một món chỉ xuất hiện một lần trong combo, hãy tăng số lượng thay vì thêm dòng mới",
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_COMBO_ITEM");
            }
        }
        return quantitiesByItemId;
    }

    private Map<Long, FnbItem> loadActiveItems(Set<Long> itemIds) {
        Map<Long, FnbItem> fnbItemsById = fnbItemRepository.findAllByIdIn(itemIds).stream()
                .filter(fnbItem -> !fnbItem.isDeleted())
                .collect(Collectors.toMap(FnbItem::getId, Function.identity()));
        if (fnbItemsById.size() != itemIds.size()) {
            throw new AppException("Có món trong combo không tồn tại hoặc đã bị xóa", HttpStatus.BAD_REQUEST, "INVALID_FNB_ITEM");
        }
        return fnbItemsById;
    }

    private FnbCombo saveCombo(FnbCombo fnbCombo) {
        try {
            return fnbComboRepository.saveAndFlush(fnbCombo);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw nameAlreadyUsed();
        }
    }

    private void applyRequest(FnbCombo fnbCombo, FnbComboRequest fnbComboRequest) {
        fnbCombo.setName(fnbComboRequest.name().trim());
        fnbCombo.setDescription(blankToNull(fnbComboRequest.description()));
        fnbCombo.setPrice(fnbComboRequest.price());
        fnbCombo.setImageUrl(blankToNull(fnbComboRequest.imageUrl()));
        fnbCombo.setStatus(fnbComboRequest.status());
    }

    private static AppException nameAlreadyUsed() {
        return new AppException("Tên combo đã tồn tại", HttpStatus.CONFLICT, "FNB_NAME_EXISTS");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
