package com.cinema.modules.booking.util;

import com.cinema.modules.cinema.dto.response.SeatLayoutSeatResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class SeatGapRule {

    private SeatGapRule() {
    }

    public static List<String> findOrphanSeatLabels(
            List<SeatLayoutSeatResponse> layoutSeats, Set<Long> selectedSeatIds, Set<Long> blockedSeatIds) {
        Map<String, List<SeatLayoutSeatResponse>> seatsByRow = new LinkedHashMap<>();
        layoutSeats.stream()
                .sorted(Comparator.comparing(SeatLayoutSeatResponse::row).thenComparingInt(SeatLayoutSeatResponse::number))
                .forEach(layoutSeat -> seatsByRow.computeIfAbsent(layoutSeat.row(), row -> new ArrayList<>()).add(layoutSeat));
        TreeSet<String> orphanLabels = new TreeSet<>(Comparator.comparing(SeatGapRule::rowOfLabel)
                .thenComparingInt(SeatGapRule::numberOfLabel));
        for (List<SeatLayoutSeatResponse> rowSeats : seatsByRow.values()) {
            for (int i = 0; i < rowSeats.size(); i++) {
                if (!selectedSeatIds.contains(rowSeats.get(i).id())) {
                    continue;
                }
                collectOrphan(rowSeats, i, -1, selectedSeatIds, blockedSeatIds, orphanLabels);
                collectOrphan(rowSeats, i, 1, selectedSeatIds, blockedSeatIds, orphanLabels);
            }
        }
        return List.copyOf(orphanLabels);
    }

    private static void collectOrphan(
            List<SeatLayoutSeatResponse> rowSeats,
            int selectedIndex,
            int direction,
            Set<Long> selectedSeatIds,
            Set<Long> blockedSeatIds,
            Set<String> orphanLabels) {
        int freeIndex = selectedIndex + direction;
        if (!isAdjacent(rowSeats, selectedIndex, freeIndex)) {
            return;
        }
        SeatLayoutSeatResponse freeSeat = rowSeats.get(freeIndex);
        if (!isFree(freeSeat, selectedSeatIds, blockedSeatIds)) {
            return;
        }
        int beyondIndex = freeIndex + direction;
        boolean beyondIsFreeNeighbor = isAdjacent(rowSeats, freeIndex, beyondIndex)
                && isFree(rowSeats.get(beyondIndex), selectedSeatIds, blockedSeatIds);
        if (!beyondIsFreeNeighbor) {
            orphanLabels.add(freeSeat.row() + freeSeat.number());
        }
    }

    private static boolean isAdjacent(List<SeatLayoutSeatResponse> rowSeats, int firstIndex, int secondIndex) {
        if (secondIndex < 0 || secondIndex >= rowSeats.size()) {
            return false;
        }
        SeatLayoutSeatResponse leftSeat = rowSeats.get(Math.min(firstIndex, secondIndex));
        SeatLayoutSeatResponse rightSeat = rowSeats.get(Math.max(firstIndex, secondIndex));
        return leftSeat.number() + leftSeat.span() == rightSeat.number();
    }

    private static boolean isFree(SeatLayoutSeatResponse layoutSeat, Set<Long> selectedSeatIds, Set<Long> blockedSeatIds) {
        return !selectedSeatIds.contains(layoutSeat.id()) && !blockedSeatIds.contains(layoutSeat.id());
    }

    private static String rowOfLabel(String label) {
        return label.replaceAll("\\d+$", "");
    }

    private static int numberOfLabel(String label) {
        return Integer.parseInt(label.substring(rowOfLabel(label).length()));
    }
}
