package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.request.GenerateSeatsRequest;
import com.cinema.modules.cinema.dto.request.SeatCellRequest;
import com.cinema.modules.cinema.dto.request.SeatMapRequest;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.entity.Seat;
import com.cinema.modules.cinema.entity.SeatType;
import com.cinema.modules.cinema.enums.SeatStatus;
import com.cinema.modules.cinema.repository.HallRepository;
import com.cinema.modules.cinema.repository.SeatRepository;
import com.cinema.modules.cinema.repository.SeatTypeRepository;
import com.cinema.modules.cinema.service.interfaces.ISeatMapService;
import com.cinema.modules.cinema.util.CinemaMapper;
import com.cinema.modules.cinema.util.SeatMapValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeatMapService implements ISeatMapService {

    private static final int MAX_ROWS = 26;
    private static final char FIRST_ROW = 'A';

    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final CinemaAccessGuard cinemaAccessGuard;
    private final HallUsageChecker hallUsageChecker;
    private final PublicCinemaCache publicCinemaCache;
    private final Clock clock;

    public SeatMapService(
            HallRepository hallRepository,
            SeatRepository seatRepository,
            SeatTypeRepository seatTypeRepository,
            CinemaAccessGuard cinemaAccessGuard,
            HallUsageChecker hallUsageChecker,
            PublicCinemaCache publicCinemaCache,
            Clock clock) {
        this.hallRepository = hallRepository;
        this.seatRepository = seatRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.cinemaAccessGuard = cinemaAccessGuard;
        this.hallUsageChecker = hallUsageChecker;
        this.publicCinemaCache = publicCinemaCache;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public SeatLayoutResponse getSeatMap(Long actorUserId, Long hallId) {
        Hall hall = cinemaAccessGuard.requireAccessibleHall(actorUserId, hallId);
        return CinemaMapper.toSeatLayoutResponse(
                hall, seatRepository.findAllByHallIdOrderBySeatRowAscSeatNumberAsc(hallId));
    }

    @Transactional
    @Override
    public SeatLayoutResponse saveSeatMap(Long actorUserId, Long hallId, SeatMapRequest seatMapRequest) {
        Hall hall = cinemaAccessGuard.requireAccessibleHall(actorUserId, hallId);
        if (hall.getVersion() != seatMapRequest.version()) {
            throw seatMapConflict();
        }
        return applySeatCells(hall, seatMapRequest.seats());
    }

    @Transactional
    @Override
    public SeatLayoutResponse generateSeats(
            Long actorUserId, Long hallId, GenerateSeatsRequest generateSeatsRequest) {
        Hall hall = cinemaAccessGuard.requireAccessibleHall(actorUserId, hallId);
        if (seatRepository.countByHallId(hallId) > 0) {
            throw new AppException(
                    "Phòng chiếu đã có sơ đồ ghế, hãy chỉnh sửa trực tiếp trên sơ đồ",
                    HttpStatus.CONFLICT,
                    "HALL_ALREADY_HAS_SEATS");
        }
        SeatType seatType = loadSeatTypesByCode().get(generateSeatsRequest.seatTypeCode());
        if (seatType == null) {
            throw new AppException(
                    "Loại ghế '" + generateSeatsRequest.seatTypeCode() + "' không tồn tại",
                    HttpStatus.BAD_REQUEST,
                    SeatMapValidator.INVALID_SEAT_TYPE);
        }
        List<SeatCellRequest> seatCellRequests = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < Math.min(generateSeatsRequest.rowCount(), MAX_ROWS); rowIndex++) {
            String row = String.valueOf((char) (FIRST_ROW + rowIndex));
            for (int number = 1; number + seatType.getSeatSpan() - 1 <= generateSeatsRequest.columnCount();
                    number += seatType.getSeatSpan()) {
                seatCellRequests.add(new SeatCellRequest(row, number, seatType.getCode(), SeatStatus.ACTIVE));
            }
        }
        return applySeatCells(hall, seatCellRequests);
    }

    private SeatLayoutResponse applySeatCells(Hall hall, List<SeatCellRequest> seatCellRequests) {
        Map<String, SeatType> seatTypesByCode = loadSeatTypesByCode();
        SeatMapValidator.validate(seatCellRequests, seatTypesByCode);

        List<Seat> existingSeats = seatRepository.findAllByHallIdOrderBySeatRowAscSeatNumberAsc(hall.getId());
        Map<String, Seat> existingSeatsByLabel = existingSeats.stream()
                .collect(Collectors.toMap(
                        seat -> SeatMapValidator.label(seat.getSeatRow(), seat.getSeatNumber()),
                        Function.identity()));
        Set<String> requestedLabels = seatCellRequests.stream()
                .map(seatCellRequest -> SeatMapValidator.label(seatCellRequest.row(), seatCellRequest.number()))
                .collect(Collectors.toSet());

        List<Seat> removedSeats = existingSeats.stream()
                .filter(seat -> !requestedLabels.contains(SeatMapValidator.label(seat.getSeatRow(), seat.getSeatNumber())))
                .toList();
        rejectRemovalOfSeatsInUse(removedSeats);
        if (!removedSeats.isEmpty()) {
            seatRepository.deleteAllByIdInBatch(removedSeats.stream().map(Seat::getId).toList());
        }

        List<Seat> resultingSeats = new ArrayList<>();
        for (SeatCellRequest seatCellRequest : seatCellRequests) {
            String seatLabel = SeatMapValidator.label(seatCellRequest.row(), seatCellRequest.number());
            Seat seat = existingSeatsByLabel.get(seatLabel);
            if (seat == null) {
                seat = new Seat();
                seat.setHallId(hall.getId());
                seat.setSeatRow(seatCellRequest.row());
                seat.setSeatNumber(seatCellRequest.number());
            }
            seat.setSeatType(seatTypesByCode.get(seatCellRequest.seatTypeCode()));
            seat.setStatus(seatCellRequest.status());
            resultingSeats.add(seat);
        }
        seatRepository.saveAll(resultingSeats);

        hall.setTotalSeats(resultingSeats.size());
        hall.setSeatMapUpdatedAt(Instant.now(clock));
        Hall savedHall = hallRepository.saveAndFlush(hall);
        publicCinemaCache.evictAfterCommit();
        return CinemaMapper.toSeatLayoutResponse(savedHall, resultingSeats);
    }

    private void rejectRemovalOfSeatsInUse(List<Seat> removedSeats) {
        if (removedSeats.isEmpty()) {
            return;
        }
        Set<Long> seatIdsInUse = hallUsageChecker.findSeatIdsInUse(removedSeats.stream().map(Seat::getId).toList());
        if (seatIdsInUse.isEmpty()) {
            return;
        }
        String seatLabelsInUse = removedSeats.stream()
                .filter(seat -> seatIdsInUse.contains(seat.getId()))
                .map(seat -> SeatMapValidator.label(seat.getSeatRow(), seat.getSeatNumber()))
                .limit(10)
                .collect(Collectors.joining(", "));
        throw new AppException(
                "Không thể xóa ghế đã có lượt giữ chỗ hoặc vé: " + seatLabelsInUse
                        + ". Hãy chuyển ghế sang trạng thái bảo trì thay vì xóa",
                HttpStatus.CONFLICT,
                "SEAT_IN_USE");
    }

    private Map<String, SeatType> loadSeatTypesByCode() {
        Map<String, SeatType> seatTypesByCode = new HashMap<>();
        seatTypeRepository.findAllByOrderByIdAsc().forEach(seatType -> seatTypesByCode.put(seatType.getCode(), seatType));
        return seatTypesByCode;
    }

    static AppException seatMapConflict() {
        return new AppException(
                "Sơ đồ ghế đã được người khác cập nhật, hãy tải lại trang để lấy bản mới nhất",
                HttpStatus.CONFLICT,
                "SEAT_MAP_CONFLICT");
    }
}
