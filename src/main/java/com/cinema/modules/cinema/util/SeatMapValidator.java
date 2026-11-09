package com.cinema.modules.cinema.util;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.request.SeatCellRequest;
import com.cinema.modules.cinema.entity.SeatType;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SeatMapValidator {

    public static final int MAX_COLUMNS = 40;
    public static final String INVALID_SEAT_MAP = "INVALID_SEAT_MAP";
    public static final String INVALID_SEAT_TYPE = "INVALID_SEAT_TYPE";

    private SeatMapValidator() {
    }

    public static String label(String row, int number) {
        return row + number;
    }

    public static void validate(List<SeatCellRequest> seatCellRequests, Map<String, SeatType> seatTypesByCode) {
        Set<String> labels = new HashSet<>();
        Map<String, String> occupiedCells = new HashMap<>();
        for (SeatCellRequest seatCellRequest : seatCellRequests) {
            String seatLabel = label(seatCellRequest.row(), seatCellRequest.number());
            if (!labels.add(seatLabel)) {
                throw invalid("Ghế " + seatLabel + " bị khai báo trùng");
            }
            SeatType seatType = seatTypesByCode.get(seatCellRequest.seatTypeCode());
            if (seatType == null) {
                throw new AppException(
                        "Loại ghế '" + seatCellRequest.seatTypeCode() + "' không tồn tại",
                        HttpStatus.BAD_REQUEST,
                        INVALID_SEAT_TYPE);
            }
            int lastColumn = seatCellRequest.number() + seatType.getSeatSpan() - 1;
            if (lastColumn > MAX_COLUMNS) {
                throw invalid("Ghế " + seatLabel + " vượt quá cột cuối cùng của sơ đồ (tối đa " + MAX_COLUMNS + " cột)");
            }
            for (int column = seatCellRequest.number(); column <= lastColumn; column++) {
                String cellKey = seatCellRequest.row() + ":" + column;
                String occupyingSeatLabel = occupiedCells.putIfAbsent(cellKey, seatLabel);
                if (occupyingSeatLabel != null) {
                    throw invalid("Ghế " + seatLabel + " chồng lên ghế " + occupyingSeatLabel);
                }
            }
        }
    }

    private static AppException invalid(String message) {
        return new AppException(message, HttpStatus.BAD_REQUEST, INVALID_SEAT_MAP);
    }
}
