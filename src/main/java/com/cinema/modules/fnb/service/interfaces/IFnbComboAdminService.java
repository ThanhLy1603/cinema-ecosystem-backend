package com.cinema.modules.fnb.service.interfaces;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.fnb.dto.request.FnbComboRequest;
import com.cinema.modules.fnb.dto.response.FnbComboResponse;
import com.cinema.modules.fnb.enums.FnbStatus;

public interface IFnbComboAdminService {

    PageResponse<FnbComboResponse> searchCombos(String keyword, FnbStatus status, boolean deleted, int page, int size);

    FnbComboResponse createCombo(FnbComboRequest fnbComboRequest);

    FnbComboResponse updateCombo(Long comboId, FnbComboRequest fnbComboRequest);

    void deleteCombo(Long comboId);

    FnbComboResponse restoreCombo(Long comboId);
}
