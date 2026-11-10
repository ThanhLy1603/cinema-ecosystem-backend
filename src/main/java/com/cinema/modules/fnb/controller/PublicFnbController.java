package com.cinema.modules.fnb.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.fnb.dto.response.FnbMenuResponse;
import com.cinema.modules.fnb.service.interfaces.IFnbPublicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fnb/public")
public class PublicFnbController {

    private final IFnbPublicService fnbPublicService;

    public PublicFnbController(IFnbPublicService fnbPublicService) {
        this.fnbPublicService = fnbPublicService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FnbMenuResponse>> getMenu() {
        return ResponseEntity.ok(ApiResponse.success(fnbPublicService.getMenu()));
    }
}
