package com.cinema.modules.fnb.service.interfaces;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.fnb.dto.request.FnbItemRequest;
import com.cinema.modules.fnb.dto.response.FnbItemResponse;
import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;

import java.util.List;

public interface IFnbItemAdminService {

    PageResponse<FnbItemResponse> searchItems(
            String keyword, FnbCategory category, FnbStatus status, boolean deleted, int page, int size);

    List<FnbItemResponse> listActiveItems();

    FnbItemResponse createItem(FnbItemRequest fnbItemRequest);

    FnbItemResponse updateItem(Long itemId, FnbItemRequest fnbItemRequest);

    void deleteItem(Long itemId);

    FnbItemResponse restoreItem(Long itemId);
}
