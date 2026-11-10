package com.cinema.modules.fnb.service;

import com.cinema.modules.fnb.dto.response.FnbComboResponse;
import com.cinema.modules.fnb.dto.response.FnbItemResponse;
import com.cinema.modules.fnb.dto.response.FnbMenuResponse;
import com.cinema.modules.fnb.enums.FnbStatus;
import com.cinema.modules.fnb.repository.FnbComboRepository;
import com.cinema.modules.fnb.repository.FnbItemRepository;
import com.cinema.modules.fnb.service.interfaces.IFnbPublicService;
import com.cinema.modules.fnb.util.FnbMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FnbPublicService implements IFnbPublicService {

    private final FnbComboRepository fnbComboRepository;
    private final FnbItemRepository fnbItemRepository;

    public FnbPublicService(FnbComboRepository fnbComboRepository, FnbItemRepository fnbItemRepository) {
        this.fnbComboRepository = fnbComboRepository;
        this.fnbItemRepository = fnbItemRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public FnbMenuResponse getMenu() {
        List<FnbComboResponse> fnbComboResponses =
                fnbComboRepository.findAllByDeletedFalseAndStatusOrderByNameAscIdAsc(FnbStatus.AVAILABLE).stream()
                        .map(FnbMapper::toFnbComboResponse)
                        .filter(FnbComboResponse::orderable)
                        .toList();
        List<FnbItemResponse> fnbItemResponses =
                fnbItemRepository.findAllByDeletedFalseAndStatusOrderByCategoryAscNameAscIdAsc(FnbStatus.AVAILABLE)
                        .stream()
                        .map(FnbMapper::toFnbItemResponse)
                        .toList();
        return new FnbMenuResponse(fnbComboResponses, fnbItemResponses);
    }
}
