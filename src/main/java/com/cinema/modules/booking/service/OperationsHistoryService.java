package com.cinema.modules.booking.service;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.scope.CinemaScope;
import com.cinema.modules.auth.scope.CinemaScopeResolver;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.booking.dto.response.OperationsFnbDeliveryResponse;
import com.cinema.modules.booking.dto.response.OperationsScanLogResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.FnbDeliveryLog;
import com.cinema.modules.booking.entity.TicketScanLog;
import com.cinema.modules.booking.enums.CheckInResultCode;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.FnbDeliveryLogRepository;
import com.cinema.modules.booking.repository.TicketScanLogRepository;
import com.cinema.modules.booking.service.interfaces.IOperationsHistoryService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class OperationsHistoryService implements IOperationsHistoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TicketScanLogRepository ticketScanLogRepository;
    private final FnbDeliveryLogRepository fnbDeliveryLogRepository;
    private final BookingRepository bookingRepository;
    private final CinemaScopeResolver cinemaScopeResolver;
    private final IUserQueryService userQueryService;
    private final ICinemaQueryService cinemaQueryService;

    public OperationsHistoryService(
            TicketScanLogRepository ticketScanLogRepository,
            FnbDeliveryLogRepository fnbDeliveryLogRepository,
            BookingRepository bookingRepository,
            CinemaScopeResolver cinemaScopeResolver,
            IUserQueryService userQueryService,
            ICinemaQueryService cinemaQueryService) {
        this.ticketScanLogRepository = ticketScanLogRepository;
        this.fnbDeliveryLogRepository = fnbDeliveryLogRepository;
        this.bookingRepository = bookingRepository;
        this.cinemaScopeResolver = cinemaScopeResolver;
        this.userQueryService = userQueryService;
        this.cinemaQueryService = cinemaQueryService;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<OperationsScanLogResponse> findScanLogs(
            Long managerUserId,
            Long cinemaId,
            Long staffUserId,
            CheckInResultCode checkInResultCode,
            Instant from,
            Instant to,
            int page,
            int size) {
        CinemaScope cinemaScope = cinemaScopeResolver.resolve(managerUserId, cinemaId);
        if (cinemaScope.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }
        Specification<TicketScanLog> specification = (ticketScanLog, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = commonPredicates(
                    ticketScanLog, criteriaBuilder, cinemaScope, staffUserId, from, to);
            if (checkInResultCode != null) {
                predicates.add(criteriaBuilder.equal(ticketScanLog.get("resultCode"), checkInResultCode));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<TicketScanLog> ticketScanLogs = ticketScanLogRepository.findAll(specification, pageRequest(page, size));
        Map<Long, String> bookingCodes = findBookingCodes(
                ticketScanLogs.getContent().stream().map(TicketScanLog::getBookingId).toList());
        Map<Long, String> staffNames = findStaffNames(
                ticketScanLogs.getContent().stream().map(TicketScanLog::getStaffUserId).toList());
        Map<Long, String> cinemaNames = cinemaQueryService.findCinemaNames(
                ticketScanLogs.getContent().stream().map(TicketScanLog::getCinemaId).distinct().toList());
        return PageResponse.of(ticketScanLogs, ticketScanLogs.getContent().stream()
                .map(ticketScanLog -> new OperationsScanLogResponse(
                        ticketScanLog.getId(),
                        ticketScanLog.getBookingId() == null ? null : bookingCodes.get(ticketScanLog.getBookingId()),
                        ticketScanLog.getScannedCode(),
                        ticketScanLog.getAction(),
                        ticketScanLog.getResultCode(),
                        ticketScanLog.getTicketCount(),
                        ticketScanLog.getStaffUserId(),
                        staffNames.get(ticketScanLog.getStaffUserId()),
                        ticketScanLog.getCinemaId(),
                        cinemaNames.get(ticketScanLog.getCinemaId()),
                        ticketScanLog.getCreatedAt()))
                .toList());
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<OperationsFnbDeliveryResponse> findFnbDeliveries(
            Long managerUserId, Long cinemaId, Long staffUserId, Instant from, Instant to, int page, int size) {
        CinemaScope cinemaScope = cinemaScopeResolver.resolve(managerUserId, cinemaId);
        if (cinemaScope.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }
        Specification<FnbDeliveryLog> specification = (fnbDeliveryLog, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.and(commonPredicates(
                        fnbDeliveryLog, criteriaBuilder, cinemaScope, staffUserId, from, to)
                        .toArray(new Predicate[0]));
        Page<FnbDeliveryLog> fnbDeliveryLogs = fnbDeliveryLogRepository.findAll(specification, pageRequest(page, size));
        Map<Long, String> staffNames = findStaffNames(
                fnbDeliveryLogs.getContent().stream().map(FnbDeliveryLog::getStaffUserId).toList());
        Map<Long, String> cinemaNames = cinemaQueryService.findCinemaNames(
                fnbDeliveryLogs.getContent().stream().map(FnbDeliveryLog::getCinemaId).distinct().toList());
        return PageResponse.of(fnbDeliveryLogs, fnbDeliveryLogs.getContent().stream()
                .map(fnbDeliveryLog -> new OperationsFnbDeliveryResponse(
                        fnbDeliveryLog.getId(),
                        fnbDeliveryLog.getBookingCode(),
                        fnbDeliveryLog.getProductName(),
                        fnbDeliveryLog.getQuantity(),
                        fnbDeliveryLog.getStaffUserId(),
                        staffNames.get(fnbDeliveryLog.getStaffUserId()),
                        fnbDeliveryLog.getCinemaId(),
                        cinemaNames.get(fnbDeliveryLog.getCinemaId()),
                        fnbDeliveryLog.getCreatedAt()))
                .toList());
    }

    private <T> List<Predicate> commonPredicates(
            Root<T> root,
            CriteriaBuilder criteriaBuilder,
            CinemaScope cinemaScope,
            Long staffUserId,
            Instant from,
            Instant to) {
        List<Predicate> predicates = new ArrayList<>();
        if (!cinemaScope.allCinemas()) {
            predicates.add(root.get("cinemaId").in(cinemaScope.cinemaIds()));
        }
        if (staffUserId != null) {
            predicates.add(criteriaBuilder.equal(root.get("staffUserId"), staffUserId));
        }
        if (from != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
        }
        if (to != null) {
            predicates.add(criteriaBuilder.lessThan(root.<Instant>get("createdAt"), to));
        }
        return predicates;
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "id"));
    }

    private Map<Long, String> findBookingCodes(Collection<Long> bookingIds) {
        return bookingRepository.findAllById(bookingIds.stream().filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Booking::getId, Booking::getBookingCode));
    }

    private Map<Long, String> findStaffNames(Collection<Long> staffUserIds) {
        Map<Long, String> staffNames = new HashMap<>();
        for (Long staffUserId : staffUserIds.stream().distinct().toList()) {
            staffNames.put(staffUserId, userQueryService.findContact(staffUserId)
                    .map(UserContactResponse::fullName)
                    .orElse("Nhân viên #" + staffUserId));
        }
        return staffNames;
    }
}
