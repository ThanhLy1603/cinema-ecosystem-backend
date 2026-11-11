package com.cinema.modules.cinema.dto.response;

public record SeatLabelInfoResponse(Long seatId, String row, int number, String seatTypeCode, String seatTypeName) {
}
