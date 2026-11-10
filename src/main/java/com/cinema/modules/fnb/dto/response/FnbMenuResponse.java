package com.cinema.modules.fnb.dto.response;

import java.util.List;

public record FnbMenuResponse(List<FnbComboResponse> combos, List<FnbItemResponse> items) {
}
