package com.cinema.modules.fnb.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.fnb.dto.request.FnbItemRequest;
import com.cinema.modules.fnb.dto.response.FnbItemResponse;
import com.cinema.modules.fnb.entity.FnbItem;
import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;
import com.cinema.modules.fnb.repository.FnbComboRepository;
import com.cinema.modules.fnb.repository.FnbItemRepository;
import com.cinema.modules.fnb.service.interfaces.IFnbItemAdminService;
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
import java.util.List;

@Service
public class FnbItemAdminService implements IFnbItemAdminService {

    static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final FnbItemRepository fnbItemRepository;
    private final FnbComboRepository fnbComboRepository;
    private final Clock clock;

    public FnbItemAdminService(
            FnbItemRepository fnbItemRepository, FnbComboRepository fnbComboRepository, Clock clock) {
        this.fnbItemRepository = fnbItemRepository;
        this.fnbComboRepository = fnbComboRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<FnbItemResponse> searchItems(
            String keyword, FnbCategory category, FnbStatus status, boolean deleted, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE),
                Sort.by("category", "name", "id"));
        Page<FnbItem> fnbItemPage =
                fnbItemRepository.findAll(FnbSpecifications.searchItems(keyword, category, status, deleted), pageable);
        return PageResponse.of(
                fnbItemPage, fnbItemPage.getContent().stream().map(FnbMapper::toFnbItemResponse).toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<FnbItemResponse> listActiveItems() {
        return fnbItemRepository.findAllByDeletedFalseOrderByCategoryAscNameAscIdAsc().stream()
                .map(FnbMapper::toFnbItemResponse)
                .toList();
    }

    @Transactional
    @Override
    public FnbItemResponse createItem(FnbItemRequest fnbItemRequest) {
        String name = fnbItemRequest.name().trim();
        if (fnbItemRepository.existsByNameIgnoreCaseAndDeletedFalse(name)) {
            throw nameAlreadyUsed();
        }
        FnbItem fnbItem = new FnbItem();
        applyRequest(fnbItem, fnbItemRequest);
        return saveItem(fnbItem);
    }

    @Transactional
    @Override
    public FnbItemResponse updateItem(Long itemId, FnbItemRequest fnbItemRequest) {
        FnbItem fnbItem = loadItem(itemId, false);
        String name = fnbItemRequest.name().trim();
        if (fnbItemRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(name, itemId)) {
            throw nameAlreadyUsed();
        }
        applyRequest(fnbItem, fnbItemRequest);
        return saveItem(fnbItem);
    }

    @Transactional
    @Override
    public void deleteItem(Long itemId) {
        FnbItem fnbItem = loadItem(itemId, false);
        if (fnbComboRepository.countActiveCombosUsingItem(itemId) > 0) {
            throw new AppException(
                    "Món đang nằm trong combo, hãy gỡ khỏi các combo trước khi xóa", HttpStatus.CONFLICT, "FNB_ITEM_IN_COMBO");
        }
        fnbItem.markDeleted(Instant.now(clock));
        fnbItemRepository.save(fnbItem);
    }

    @Transactional
    @Override
    public FnbItemResponse restoreItem(Long itemId) {
        FnbItem fnbItem = loadItem(itemId, true);
        if (fnbItemRepository.existsByNameIgnoreCaseAndDeletedFalse(fnbItem.getName())) {
            throw nameAlreadyUsed();
        }
        fnbItem.restore();
        return saveItem(fnbItem);
    }

    private FnbItem loadItem(Long itemId, boolean deleted) {
        return fnbItemRepository.findById(itemId)
                .filter(fnbItem -> fnbItem.isDeleted() == deleted)
                .orElseThrow(() -> new AppException("Không tìm thấy món", HttpStatus.NOT_FOUND, "FNB_ITEM_NOT_FOUND"));
    }

    private FnbItemResponse saveItem(FnbItem fnbItem) {
        try {
            return FnbMapper.toFnbItemResponse(fnbItemRepository.saveAndFlush(fnbItem));
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw nameAlreadyUsed();
        }
    }

    private void applyRequest(FnbItem fnbItem, FnbItemRequest fnbItemRequest) {
        fnbItem.setName(fnbItemRequest.name().trim());
        fnbItem.setCategory(fnbItemRequest.category());
        fnbItem.setPrice(fnbItemRequest.price());
        fnbItem.setImageUrl(blankToNull(fnbItemRequest.imageUrl()));
        fnbItem.setStatus(fnbItemRequest.status());
    }

    private static AppException nameAlreadyUsed() {
        return new AppException("Tên món đã tồn tại", HttpStatus.CONFLICT, "FNB_NAME_EXISTS");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
