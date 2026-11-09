package com.cinema.modules.cinema.util;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.request.SeatCellRequest;
import com.cinema.modules.cinema.entity.SeatType;
import com.cinema.modules.cinema.enums.SeatStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatMapValidatorTest {

    private final Map<String, SeatType> seatTypesByCode = Map.of(
            "STANDARD", seatType("STANDARD", 1),
            "VIP", seatType("VIP", 1),
            "SWEETBOX", seatType("SWEETBOX", 2));

    @Test
    void acceptsGridWithAislesAndDoubleSeats() {
        List<SeatCellRequest> seatCellRequests = List.of(
                cell("A", 1, "STANDARD"),
                cell("A", 2, "VIP"),
                cell("A", 5, "STANDARD"),
                cell("B", 1, "SWEETBOX"),
                cell("B", 3, "SWEETBOX"));

        assertDoesNotThrow(() -> SeatMapValidator.validate(seatCellRequests, seatTypesByCode));
    }

    @Test
    void rejectsDuplicateRowAndNumber() {
        List<SeatCellRequest> seatCellRequests = List.of(cell("A", 1, "STANDARD"), cell("A", 1, "VIP"));

        AppException appException =
                assertThrows(AppException.class, () -> SeatMapValidator.validate(seatCellRequests, seatTypesByCode));

        assertEquals(SeatMapValidator.INVALID_SEAT_MAP, appException.getErrorCode());
        assertTrue(appException.getMessage().contains("A1"));
    }

    @Test
    void rejectsSeatThatSitsInsideADoubleSeat() {
        List<SeatCellRequest> seatCellRequests = List.of(cell("C", 4, "SWEETBOX"), cell("C", 5, "STANDARD"));

        AppException appException =
                assertThrows(AppException.class, () -> SeatMapValidator.validate(seatCellRequests, seatTypesByCode));

        assertEquals(SeatMapValidator.INVALID_SEAT_MAP, appException.getErrorCode());
        assertTrue(appException.getMessage().contains("C5"));
        assertTrue(appException.getMessage().contains("C4"));
    }

    @Test
    void rejectsDoubleSeatThatOverflowsLastColumn() {
        List<SeatCellRequest> seatCellRequests = List.of(cell("A", SeatMapValidator.MAX_COLUMNS, "SWEETBOX"));

        AppException appException =
                assertThrows(AppException.class, () -> SeatMapValidator.validate(seatCellRequests, seatTypesByCode));

        assertEquals(SeatMapValidator.INVALID_SEAT_MAP, appException.getErrorCode());
    }

    @Test
    void rejectsUnknownSeatType() {
        List<SeatCellRequest> seatCellRequests = List.of(cell("A", 1, "GOLD"));

        AppException appException =
                assertThrows(AppException.class, () -> SeatMapValidator.validate(seatCellRequests, seatTypesByCode));

        assertEquals(SeatMapValidator.INVALID_SEAT_TYPE, appException.getErrorCode());
    }

    private static SeatCellRequest cell(String row, int number, String seatTypeCode) {
        return new SeatCellRequest(row, number, seatTypeCode, SeatStatus.ACTIVE);
    }

    private static SeatType seatType(String code, int seatSpan) {
        SeatType seatType = new SeatType();
        seatType.setCode(code);
        seatType.setName(code);
        seatType.setDefaultSurcharge(BigDecimal.ZERO);
        seatType.setSeatSpan(seatSpan);
        return seatType;
    }
}
