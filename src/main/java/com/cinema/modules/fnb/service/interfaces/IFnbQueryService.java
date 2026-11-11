package com.cinema.modules.fnb.service.interfaces;

import com.cinema.modules.fnb.dto.response.FnbProductInfoResponse;

import java.util.Collection;
import java.util.Map;

public interface IFnbQueryService {

    Map<Long, FnbProductInfoResponse> findComboInfos(Collection<Long> comboIds);

    Map<Long, FnbProductInfoResponse> findItemInfos(Collection<Long> itemIds);
}
