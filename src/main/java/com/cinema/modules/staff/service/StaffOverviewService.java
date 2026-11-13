package com.cinema.modules.staff.service;

import com.cinema.common.time.VietnamTime;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.booking.dto.response.FnbPickupShowtimeGroupResponse;
import com.cinema.modules.booking.dto.response.ShowtimeTicketStatsResponse;
import com.cinema.modules.booking.dto.response.TicketQueueShowtimeGroupResponse;
import com.cinema.modules.booking.service.interfaces.IFnbPickupService;
import com.cinema.modules.booking.service.interfaces.IShowtimeTicketStatsService;
import com.cinema.modules.booking.service.interfaces.ITicketCheckInQueueService;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import com.cinema.modules.staff.dto.response.ShowtimeOverviewResponse;
import com.cinema.modules.staff.dto.response.StaffOverviewResponse;
import com.cinema.modules.staff.service.interfaces.IStaffOverviewService;
import com.cinema.modules.staff.service.interfaces.IStaffShiftService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StaffOverviewService implements IStaffOverviewService {

    private final IBranchAccessService branchAccessService;
    private final IShowtimeQueryService showtimeQueryService;
    private final IMovieQueryService movieQueryService;
    private final ICinemaQueryService cinemaQueryService;
    private final IShowtimeTicketStatsService showtimeTicketStatsService;
    private final IFnbPickupService fnbPickupService;
    private final ITicketCheckInQueueService ticketCheckInQueueService;
    private final IStaffShiftService staffShiftService;
    private final Clock clock;

    public StaffOverviewService(
            IBranchAccessService branchAccessService,
            IShowtimeQueryService showtimeQueryService,
            IMovieQueryService movieQueryService,
            ICinemaQueryService cinemaQueryService,
            IShowtimeTicketStatsService showtimeTicketStatsService,
            IFnbPickupService fnbPickupService,
            ITicketCheckInQueueService ticketCheckInQueueService,
            IStaffShiftService staffShiftService,
            Clock clock) {
        this.branchAccessService = branchAccessService;
        this.showtimeQueryService = showtimeQueryService;
        this.movieQueryService = movieQueryService;
        this.cinemaQueryService = cinemaQueryService;
        this.showtimeTicketStatsService = showtimeTicketStatsService;
        this.fnbPickupService = fnbPickupService;
        this.ticketCheckInQueueService = ticketCheckInQueueService;
        this.staffShiftService = staffShiftService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public StaffOverviewResponse findOverview(Long staffUserId, Long cinemaId) {
        branchAccessService.assertCanAccessCinema(staffUserId, cinemaId);
        Instant now = Instant.now(clock);
        Instant endOfDay = VietnamTime.startOfNextDay(LocalDate.ofInstant(now, VietnamTime.ZONE));
        Set<Long> hallIds = cinemaQueryService.findHallIdsByCinemaIds(List.of(cinemaId));
        List<ShowtimeInfoResponse> showtimeInfoResponses = showtimeQueryService
                .findActiveShowtimeInfosBetween(now, endOfDay).stream()
                .filter(showtimeInfoResponse -> hallIds.contains(showtimeInfoResponse.hallId()))
                .sorted(Comparator.comparing(ShowtimeInfoResponse::startTime))
                .toList();
        Map<Long, MovieInfoResponse> movieInfoResponses = movieQueryService.findMovieInfos(
                showtimeInfoResponses.stream().map(ShowtimeInfoResponse::movieId).distinct().toList());
        Map<Long, HallInfoResponse> hallInfoResponses = cinemaQueryService.findHallInfos(hallIds);
        Map<Long, Integer> capacities = cinemaQueryService.countBookableSeatsByHall(hallIds);
        Map<Long, ShowtimeTicketStatsResponse> ticketStats = showtimeTicketStatsService.countByShowtimes(
                showtimeInfoResponses.stream().map(ShowtimeInfoResponse::id).toList());
        List<ShowtimeOverviewResponse> showtimeOverviewResponses = showtimeInfoResponses.stream()
                .map(showtimeInfoResponse -> toShowtimeOverview(
                        showtimeInfoResponse, now, movieInfoResponses, hallInfoResponses, capacities, ticketStats))
                .toList();
        int pendingFnbBookingCount = fnbPickupService.findQueue(staffUserId, cinemaId).showtimes().stream()
                .mapToInt(FnbPickupShowtimeGroupResponse::pendingBookingCount)
                .sum();
        int pendingCheckInTicketCount = ticketCheckInQueueService.findQueue(staffUserId, cinemaId).showtimes().stream()
                .mapToInt(TicketQueueShowtimeGroupResponse::pendingTicketCount)
                .sum();
        return new StaffOverviewResponse(
                now,
                showtimeOverviewResponses,
                pendingFnbBookingCount,
                pendingCheckInTicketCount,
                staffShiftService.findCurrentShift(staffUserId));
    }

    private ShowtimeOverviewResponse toShowtimeOverview(
            ShowtimeInfoResponse showtimeInfoResponse,
            Instant now,
            Map<Long, MovieInfoResponse> movieInfoResponses,
            Map<Long, HallInfoResponse> hallInfoResponses,
            Map<Long, Integer> capacities,
            Map<Long, ShowtimeTicketStatsResponse> ticketStats) {
        MovieInfoResponse movieInfoResponse = movieInfoResponses.get(showtimeInfoResponse.movieId());
        HallInfoResponse hallInfoResponse = hallInfoResponses.get(showtimeInfoResponse.hallId());
        int capacity = capacities.getOrDefault(showtimeInfoResponse.hallId(), 0);
        ShowtimeTicketStatsResponse showtimeTicketStatsResponse = ticketStats.getOrDefault(
                showtimeInfoResponse.id(), new ShowtimeTicketStatsResponse(0, 0, 0));
        long availableSeats = Math.max(
                0, capacity - showtimeTicketStatsResponse.soldSeats() - showtimeTicketStatsResponse.heldSeats());
        return new ShowtimeOverviewResponse(
                showtimeInfoResponse.id(),
                movieInfoResponse == null ? "Phim đã xóa" : movieInfoResponse.title(),
                hallInfoResponse == null ? "" : hallInfoResponse.name(),
                hallInfoResponse == null ? null : hallInfoResponse.screenType(),
                showtimeInfoResponse.startTime(),
                showtimeInfoResponse.endTime(),
                !showtimeInfoResponse.startTime().isAfter(now),
                capacity,
                showtimeTicketStatsResponse.soldSeats(),
                showtimeTicketStatsResponse.heldSeats(),
                availableSeats,
                showtimeTicketStatsResponse.checkedInSeats());
    }
}
