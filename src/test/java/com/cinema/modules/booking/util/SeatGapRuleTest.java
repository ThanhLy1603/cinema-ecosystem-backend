package com.cinema.modules.booking.util;

import com.cinema.modules.cinema.dto.response.SeatLayoutSeatResponse;
import com.cinema.modules.cinema.enums.SeatStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeatGapRuleTest {

    private static List<SeatLayoutSeatResponse> row(String row, int... numbers) {
        List<SeatLayoutSeatResponse> seats = new ArrayList<>();
        for (int number : numbers) {
            seats.add(seat(row, number, 1));
        }
        return seats;
    }

    private static SeatLayoutSeatResponse seat(String row, int number, int span) {
        return new SeatLayoutSeatResponse(
                row.charAt(0) * 100L + number, row, number, "STANDARD", BigDecimal.ZERO, span, SeatStatus.ACTIVE);
    }

    private static Long id(String row, int number) {
        return row.charAt(0) * 100L + number;
    }

    private static List<SeatLayoutSeatResponse> fullRow(String row, int count) {
        int[] numbers = new int[count];
        for (int i = 0; i < count; i++) {
            numbers[i] = i + 1;
        }
        return row(row, numbers);
    }

    @Test
    void aSingleSeatLeftBetweenTwoSelectedSeatsIsAnOrphan() {
        List<String> orphans = SeatGapRule.findOrphanSeatLabels(
                fullRow("A", 10), Set.of(id("A", 2), id("A", 4)), Set.of());

        assertEquals(List.of("A1", "A3"), orphans);
    }

    @Test
    void selectingFromTheWallLeavesNoOrphan() {
        assertEquals(List.of(), SeatGapRule.findOrphanSeatLabels(
                fullRow("A", 10), Set.of(id("A", 1), id("A", 2), id("A", 3)), Set.of()));
    }

    @Test
    void leavingExactlyOneSeatNextToTheWallIsAnOrphan() {
        assertEquals(List.of("A1"), SeatGapRule.findOrphanSeatLabels(
                fullRow("A", 10), Set.of(id("A", 2), id("A", 3)), Set.of()));
    }

    @Test
    void twoFreeSeatsBesideTheSelectionAreFine() {
        assertEquals(List.of(), SeatGapRule.findOrphanSeatLabels(
                fullRow("A", 10), Set.of(id("A", 3), id("A", 4)), Set.of()));
    }

    @Test
    void aFreeSeatSquashedBetweenTheSelectionAndASoldSeatIsAnOrphan() {
        assertEquals(List.of("A5"), SeatGapRule.findOrphanSeatLabels(
                fullRow("A", 10), Set.of(id("A", 3), id("A", 4)), Set.of(id("A", 6))));
    }

    @Test
    void anAislegapCountsAsAWall() {
        List<SeatLayoutSeatResponse> rowWithAisle = row("A", 1, 2, 3, 5, 6, 7);

        assertEquals(List.of("A3"), SeatGapRule.findOrphanSeatLabels(
                rowWithAisle, Set.of(id("A", 1), id("A", 2)), Set.of()));
        assertEquals(List.of(), SeatGapRule.findOrphanSeatLabels(
                rowWithAisle, Set.of(id("A", 5), id("A", 6), id("A", 7)), Set.of()));
    }

    @Test
    void aGapThatExistedBeforeAndIsNotNextToTheSelectionIsNotBlamedOnTheCustomer() {
        assertEquals(List.of(), SeatGapRule.findOrphanSeatLabels(
                fullRow("A", 10), Set.of(id("A", 1), id("A", 2)), Set.of(id("A", 5), id("A", 7))));
    }

    @Test
    void sweetboxCouplesCountAsOneSeatUnit() {
        List<SeatLayoutSeatResponse> couples = List.of(seat("L", 1, 2), seat("L", 3, 2), seat("L", 5, 2), seat("L", 7, 2));

        assertEquals(List.of("L3", "L7"), SeatGapRule.findOrphanSeatLabels(
                couples, Set.of(id("L", 1), id("L", 5)), Set.of()));
        assertEquals(List.of(), SeatGapRule.findOrphanSeatLabels(
                couples, Set.of(id("L", 1), id("L", 3)), Set.of()));
    }

    @Test
    void rowsAreIndependentAndSoldOrMaintenanceSeatsActLikeWalls() {
        List<SeatLayoutSeatResponse> layout = new ArrayList<>(fullRow("A", 6));
        layout.addAll(fullRow("B", 6));

        assertEquals(List.of("A2", "B5"), SeatGapRule.findOrphanSeatLabels(
                layout, Set.of(id("A", 1), id("B", 6)), Set.of(id("A", 3), id("B", 4))));
        assertEquals(List.of(), SeatGapRule.findOrphanSeatLabels(
                layout, Set.of(id("A", 1), id("A", 2)), Set.of(id("A", 3))));
    }
}
