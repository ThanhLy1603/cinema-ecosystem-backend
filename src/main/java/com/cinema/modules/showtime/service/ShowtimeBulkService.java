package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.BusinessZone;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.showtime.dto.request.ShowtimeDayCopyRequest;
import com.cinema.modules.showtime.dto.request.ShowtimeDuplicateRequest;
import com.cinema.modules.showtime.dto.response.BulkShowtimeItemResponse;
import com.cinema.modules.showtime.dto.response.BulkShowtimeResponse;
import com.cinema.modules.showtime.dto.response.ShowtimeConflictResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.BulkItemStatus;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.exception.ShowtimeConflictException;
import com.cinema.modules.showtime.model.BulkShowtimeCandidate;
import com.cinema.modules.showtime.model.PlannedShowtimeSlot;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import com.cinema.modules.showtime.service.interfaces.IShowtimeBulkService;
import com.cinema.modules.showtime.specification.ShowtimeSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class ShowtimeBulkService implements IShowtimeBulkService {

    static final int MAX_CANDIDATES = 200;
    private static final Sort SHOWTIME_SORT = Sort.by("startTime", "id");
    private static final String INVALID_BULK_ERROR_CODE = "INVALID_BULK_REQUEST";

    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeSlotPlanner showtimeSlotPlanner;
    private final ShowtimePersister showtimePersister;
    private final ICinemaQueryService cinemaQueryService;
    private final IBranchAccessService branchAccessService;
    private final Clock clock;

    public ShowtimeBulkService(
            ShowtimeRepository showtimeRepository,
            ShowtimeSlotPlanner showtimeSlotPlanner,
            ShowtimePersister showtimePersister,
            ICinemaQueryService cinemaQueryService,
            IBranchAccessService branchAccessService,
            Clock clock) {
        this.showtimeRepository = showtimeRepository;
        this.showtimeSlotPlanner = showtimeSlotPlanner;
        this.showtimePersister = showtimePersister;
        this.cinemaQueryService = cinemaQueryService;
        this.branchAccessService = branchAccessService;
        this.clock = clock;
    }

    @Override
    public BulkShowtimeResponse duplicateShowtime(Long actorUserId, ShowtimeDuplicateRequest showtimeDuplicateRequest) {
        Showtime sourceShowtime = showtimeRepository.findById(showtimeDuplicateRequest.sourceShowtimeId()).orElseThrow(
                () -> new AppException("Không tìm thấy suất chiếu", HttpStatus.NOT_FOUND, "SHOWTIME_NOT_FOUND"));
        Set<Long> targetHallIds = new LinkedHashSet<>(showtimeDuplicateRequest.hallIds());
        Set<LocalDate> targetDates = new TreeSet<>(showtimeDuplicateRequest.dates());
        assertWithinLimit(targetHallIds.size() * targetDates.size());
        Set<Long> allHallIds = new LinkedHashSet<>(targetHallIds);
        allHallIds.add(sourceShowtime.getHallId());
        Map<Long, HallInfoResponse> hallInfoResponses = loadAccessibleHalls(actorUserId, targetHallIds, allHallIds);
        LocalTime sourceLocalTime = BusinessZone.toLocalTime(sourceShowtime.getStartTime());
        List<BulkShowtimeCandidate> bulkShowtimeCandidates = new ArrayList<>();
        for (LocalDate targetDate : targetDates) {
            for (Long hallId : targetHallIds) {
                bulkShowtimeCandidates.add(new BulkShowtimeCandidate(
                        sourceShowtime.getMovieId(),
                        hallId,
                        BusinessZone.toInstant(targetDate, sourceLocalTime),
                        sourceShowtime.getBasePrice()));
            }
        }
        return execute(actorUserId, bulkShowtimeCandidates, hallInfoResponses, showtimeDuplicateRequest.dryRun());
    }

    @Override
    public BulkShowtimeResponse copyDay(Long actorUserId, ShowtimeDayCopyRequest showtimeDayCopyRequest) {
        Long cinemaId = showtimeDayCopyRequest.cinemaId();
        branchAccessService.assertCanAccessCinema(actorUserId, cinemaId);
        LocalDate sourceDate = showtimeDayCopyRequest.sourceDate();
        Set<LocalDate> targetDates = new TreeSet<>(showtimeDayCopyRequest.targetDates());
        if (targetDates.contains(sourceDate)) {
            throw new AppException(
                    "Ngày đích không được trùng ngày nguồn", HttpStatus.BAD_REQUEST, INVALID_BULK_ERROR_CODE);
        }
        if (!cinemaQueryService.findAllCinemaIds().contains(cinemaId)) {
            throw new AppException("Không tìm thấy cụm rạp", HttpStatus.NOT_FOUND, "CINEMA_NOT_FOUND");
        }
        Set<Long> hallIds = cinemaQueryService.findHallIdsByCinemaIds(List.of(cinemaId));
        if (hallIds.isEmpty()) {
            return summarize(showtimeDayCopyRequest.dryRun(), List.of());
        }
        Specification<Showtime> specification = Specification.where(ShowtimeSpecifications.inHalls(hallIds))
                .and(ShowtimeSpecifications.startingBetween(
                        BusinessZone.startOfDay(sourceDate), BusinessZone.startOfNextDay(sourceDate)))
                .and(ShowtimeSpecifications.withStatus(ShowtimeStatus.ACTIVE));
        List<Showtime> sourceShowtimes = showtimeRepository.findAll(specification, SHOWTIME_SORT);
        assertWithinLimit(sourceShowtimes.size() * targetDates.size());
        Map<Long, HallInfoResponse> hallInfoResponses = cinemaQueryService.findHallInfos(hallIds);
        List<BulkShowtimeCandidate> bulkShowtimeCandidates = new ArrayList<>();
        for (LocalDate targetDate : targetDates) {
            for (Showtime sourceShowtime : sourceShowtimes) {
                bulkShowtimeCandidates.add(new BulkShowtimeCandidate(
                        sourceShowtime.getMovieId(),
                        sourceShowtime.getHallId(),
                        BusinessZone.toInstant(targetDate, BusinessZone.toLocalTime(sourceShowtime.getStartTime())),
                        sourceShowtime.getBasePrice()));
            }
        }
        return execute(actorUserId, bulkShowtimeCandidates, hallInfoResponses, showtimeDayCopyRequest.dryRun());
    }

    private BulkShowtimeResponse execute(
            Long actorUserId,
            List<BulkShowtimeCandidate> bulkShowtimeCandidates,
            Map<Long, HallInfoResponse> hallInfoResponses,
            boolean dryRun) {
        Instant now = Instant.now(clock);
        Map<Long, List<PlannedShowtimeSlot>> acceptedSlotsByHallId = new HashMap<>();
        List<BulkShowtimeItemResponse> bulkShowtimeItemResponses = new ArrayList<>();
        for (BulkShowtimeCandidate bulkShowtimeCandidate : bulkShowtimeCandidates) {
            HallInfoResponse hallInfoResponse = hallInfoResponses.get(bulkShowtimeCandidate.hallId());
            bulkShowtimeItemResponses.add(processCandidate(
                    actorUserId, bulkShowtimeCandidate, hallInfoResponse, now, dryRun, acceptedSlotsByHallId));
        }
        return summarize(dryRun, bulkShowtimeItemResponses);
    }

    private BulkShowtimeItemResponse processCandidate(
            Long actorUserId,
            BulkShowtimeCandidate bulkShowtimeCandidate,
            HallInfoResponse hallInfoResponse,
            Instant now,
            boolean dryRun,
            Map<Long, List<PlannedShowtimeSlot>> acceptedSlotsByHallId) {
        try {
            PlannedShowtimeSlot plannedShowtimeSlot = showtimeSlotPlanner.plan(
                    actorUserId,
                    bulkShowtimeCandidate.movieId(),
                    bulkShowtimeCandidate.hallId(),
                    bulkShowtimeCandidate.startTime(),
                    now,
                    null);
            List<PlannedShowtimeSlot> acceptedSlots =
                    acceptedSlotsByHallId.computeIfAbsent(bulkShowtimeCandidate.hallId(), hallId -> new ArrayList<>());
            if (overlapsAccepted(plannedShowtimeSlot, acceptedSlots)) {
                return buildItem(
                        bulkShowtimeCandidate, hallInfoResponse, plannedShowtimeSlot, BulkItemStatus.CONFLICT,
                        "Trùng với một suất khác trong cùng lượt tạo", List.of(), null);
            }
            if (dryRun) {
                acceptedSlots.add(plannedShowtimeSlot);
                return buildItem(
                        bulkShowtimeCandidate, hallInfoResponse, plannedShowtimeSlot, BulkItemStatus.READY, null, List.of(), null);
            }
            Showtime savedShowtime = showtimePersister.saveWithoutOverlap(
                    newShowtime(bulkShowtimeCandidate, plannedShowtimeSlot), plannedShowtimeSlot, null);
            acceptedSlots.add(plannedShowtimeSlot);
            return buildItem(
                    bulkShowtimeCandidate, hallInfoResponse, plannedShowtimeSlot, BulkItemStatus.CREATED, null, List.of(),
                    savedShowtime.getId());
        } catch (ShowtimeConflictException showtimeConflictException) {
            return buildItem(
                    bulkShowtimeCandidate, hallInfoResponse, null, BulkItemStatus.CONFLICT,
                    showtimeConflictException.getMessage(), showtimeConflictException.getConflicts(), null);
        } catch (AppException appException) {
            return buildItem(
                    bulkShowtimeCandidate, hallInfoResponse, null, BulkItemStatus.INVALID, appException.getMessage(),
                    List.of(), null);
        }
    }

    private boolean overlapsAccepted(PlannedShowtimeSlot plannedShowtimeSlot, Collection<PlannedShowtimeSlot> acceptedSlots) {
        return acceptedSlots.stream().anyMatch(acceptedSlot ->
                plannedShowtimeSlot.startTime().isBefore(acceptedSlot.occupiedUntil())
                        && acceptedSlot.startTime().isBefore(plannedShowtimeSlot.occupiedUntil()));
    }

    private Showtime newShowtime(BulkShowtimeCandidate bulkShowtimeCandidate, PlannedShowtimeSlot plannedShowtimeSlot) {
        Showtime showtime = new Showtime();
        showtime.setMovieId(bulkShowtimeCandidate.movieId());
        showtime.setHallId(bulkShowtimeCandidate.hallId());
        showtime.setStartTime(plannedShowtimeSlot.startTime());
        showtime.setEndTime(plannedShowtimeSlot.endTime());
        showtime.setBasePrice(bulkShowtimeCandidate.basePrice());
        showtime.setStatus(ShowtimeStatus.ACTIVE);
        return showtime;
    }

    private BulkShowtimeItemResponse buildItem(
            BulkShowtimeCandidate bulkShowtimeCandidate,
            HallInfoResponse hallInfoResponse,
            PlannedShowtimeSlot plannedShowtimeSlot,
            BulkItemStatus bulkItemStatus,
            String reason,
            List<ShowtimeConflictResponse> conflicts,
            Long showtimeId) {
        return new BulkShowtimeItemResponse(
                bulkShowtimeCandidate.hallId(),
                hallInfoResponse == null ? null : hallInfoResponse.name(),
                bulkShowtimeCandidate.movieId(),
                bulkShowtimeCandidate.startTime(),
                plannedShowtimeSlot == null ? null : plannedShowtimeSlot.endTime(),
                bulkItemStatus,
                reason,
                conflicts,
                showtimeId);
    }

    private BulkShowtimeResponse summarize(boolean dryRun, List<BulkShowtimeItemResponse> bulkShowtimeItemResponses) {
        int acceptedCount = 0;
        int conflictCount = 0;
        int invalidCount = 0;
        for (BulkShowtimeItemResponse bulkShowtimeItemResponse : bulkShowtimeItemResponses) {
            switch (bulkShowtimeItemResponse.status()) {
                case READY, CREATED -> acceptedCount++;
                case CONFLICT -> conflictCount++;
                case INVALID -> invalidCount++;
            }
        }
        return new BulkShowtimeResponse(
                dryRun, bulkShowtimeItemResponses.size(), acceptedCount, conflictCount, invalidCount,
                List.copyOf(bulkShowtimeItemResponses));
    }

    private Map<Long, HallInfoResponse> loadAccessibleHalls(
            Long actorUserId, Collection<Long> targetHallIds, Collection<Long> allHallIds) {
        Map<Long, HallInfoResponse> hallInfoResponses = cinemaQueryService.findHallInfos(allHallIds);
        for (Long hallId : allHallIds) {
            HallInfoResponse hallInfoResponse = hallInfoResponses.get(hallId);
            boolean unusable = hallInfoResponse == null || (hallInfoResponse.deleted() && targetHallIds.contains(hallId));
            if (unusable) {
                throw new AppException("Không tìm thấy phòng chiếu", HttpStatus.NOT_FOUND, "HALL_NOT_FOUND");
            }
            branchAccessService.assertCanAccessCinema(actorUserId, hallInfoResponse.cinemaId());
        }
        return hallInfoResponses;
    }

    private void assertWithinLimit(int candidateCount) {
        if (candidateCount > MAX_CANDIDATES) {
            throw new AppException(
                    "Mỗi lượt chỉ tạo tối đa " + MAX_CANDIDATES + " suất chiếu, hiện đang chọn " + candidateCount,
                    HttpStatus.BAD_REQUEST,
                    INVALID_BULK_ERROR_CODE);
        }
    }
}
