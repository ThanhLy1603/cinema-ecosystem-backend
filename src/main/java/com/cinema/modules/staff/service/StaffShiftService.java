package com.cinema.modules.staff.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.scope.CinemaScope;
import com.cinema.modules.auth.scope.CinemaScopeResolver;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.payment.dto.response.CounterMethodTotalResponse;
import com.cinema.modules.payment.dto.response.CounterTransactionResponse;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.service.interfaces.IRevenueReportService;
import com.cinema.modules.staff.dto.request.CloseShiftRequest;
import com.cinema.modules.staff.dto.request.OpenShiftRequest;
import com.cinema.modules.staff.dto.response.ShiftReportResponse;
import com.cinema.modules.staff.dto.response.ShiftResponse;
import com.cinema.modules.staff.entity.StaffShift;
import com.cinema.modules.staff.enums.ShiftStatus;
import com.cinema.modules.staff.repository.StaffShiftRepository;
import com.cinema.modules.staff.service.interfaces.IStaffShiftService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class StaffShiftService implements IStaffShiftService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final long REPORT_END_PADDING_MILLIS = 1;

    private final StaffShiftRepository staffShiftRepository;
    private final IRevenueReportService revenueReportService;
    private final IBranchAccessService branchAccessService;
    private final CinemaScopeResolver cinemaScopeResolver;
    private final IUserQueryService userQueryService;
    private final ICinemaQueryService cinemaQueryService;
    private final IAuditLogService auditLogService;
    private final Clock clock;

    public StaffShiftService(
            StaffShiftRepository staffShiftRepository,
            IRevenueReportService revenueReportService,
            IBranchAccessService branchAccessService,
            CinemaScopeResolver cinemaScopeResolver,
            IUserQueryService userQueryService,
            ICinemaQueryService cinemaQueryService,
            IAuditLogService auditLogService,
            Clock clock) {
        this.staffShiftRepository = staffShiftRepository;
        this.revenueReportService = revenueReportService;
        this.branchAccessService = branchAccessService;
        this.cinemaScopeResolver = cinemaScopeResolver;
        this.userQueryService = userQueryService;
        this.cinemaQueryService = cinemaQueryService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    @Override
    public ShiftReportResponse openShift(Long staffUserId, OpenShiftRequest openShiftRequest) {
        branchAccessService.assertCanAccessCinema(staffUserId, openShiftRequest.cinemaId());
        if (staffShiftRepository.findFirstByStaffUserIdAndStatus(staffUserId, ShiftStatus.OPEN).isPresent()) {
            throw new AppException("Bạn đang có một ca chưa đóng", HttpStatus.CONFLICT, "SHIFT_ALREADY_OPEN");
        }
        StaffShift staffShift = new StaffShift();
        staffShift.setStaffUserId(staffUserId);
        staffShift.setCinemaId(openShiftRequest.cinemaId());
        staffShift.setStatus(ShiftStatus.OPEN);
        staffShift.setOpenedAt(Instant.now(clock));
        staffShift.setOpeningCash(openShiftRequest.openingCash());
        StaffShift savedStaffShift = staffShiftRepository.save(staffShift);
        auditLogService.record(staffUserId, savedStaffShift.getCinemaId(), AuditAction.SHIFT_OPENED, "SHIFT",
                String.valueOf(savedStaffShift.getId()), "Tiền đầu ca " + openShiftRequest.openingCash().toPlainString());
        return buildReport(savedStaffShift);
    }

    @Transactional(readOnly = true)
    @Override
    public ShiftReportResponse findCurrentShift(Long staffUserId) {
        return staffShiftRepository.findFirstByStaffUserIdAndStatus(staffUserId, ShiftStatus.OPEN)
                .map(this::buildReport)
                .orElse(null);
    }

    @Transactional
    @Override
    public ShiftReportResponse closeShift(Long staffUserId, CloseShiftRequest closeShiftRequest) {
        StaffShift staffShift = staffShiftRepository
                .findByStaffUserIdAndStatusForUpdate(staffUserId, ShiftStatus.OPEN)
                .orElseThrow(() -> new AppException("Bạn chưa mở ca nào", HttpStatus.CONFLICT, "NO_OPEN_SHIFT"));
        Instant closedAt = Instant.now(clock);
        BigDecimal cashSales = findCashSales(staffShift, closedAt);
        BigDecimal expectedCash = staffShift.getOpeningCash().add(cashSales);
        staffShift.setStatus(ShiftStatus.CLOSED);
        staffShift.setClosedAt(closedAt);
        staffShift.setExpectedCash(expectedCash);
        staffShift.setCountedCash(closeShiftRequest.countedCash());
        staffShift.setCashDifference(closeShiftRequest.countedCash().subtract(expectedCash));
        staffShift.setNote(closeShiftRequest.note() == null || closeShiftRequest.note().isBlank()
                ? null
                : closeShiftRequest.note().trim());
        StaffShift savedStaffShift = staffShiftRepository.save(staffShift);
        auditLogService.record(staffUserId, savedStaffShift.getCinemaId(), AuditAction.SHIFT_CLOSED, "SHIFT",
                String.valueOf(savedStaffShift.getId()),
                "Chênh lệch tiền mặt " + savedStaffShift.getCashDifference().toPlainString());
        return buildReport(savedStaffShift);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ShiftResponse> findMyShifts(Long staffUserId, int limit) {
        Specification<StaffShift> specification = (staffShift, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(staffShift.get("staffUserId"), staffUserId);
        return staffShiftRepository.findAll(specification,
                        PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "id")))
                .map(this::toShiftResponse)
                .getContent();
    }

    @Transactional(readOnly = true)
    @Override
    public ShiftReportResponse findShiftReport(Long userId, Long shiftId) {
        StaffShift staffShift = staffShiftRepository.findById(shiftId)
                .orElseThrow(() -> new AppException("Không tìm thấy ca làm việc", HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND"));
        if (!staffShift.getStaffUserId().equals(userId)) {
            branchAccessService.assertCanAccessCinema(userId, staffShift.getCinemaId());
        }
        return buildReport(staffShift);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<ShiftResponse> findShifts(
            Long managerUserId, Long cinemaId, Long staffUserId, Instant from, Instant to, int page, int size) {
        CinemaScope cinemaScope = cinemaScopeResolver.resolve(managerUserId, cinemaId);
        if (cinemaScope.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }
        Specification<StaffShift> specification = (staffShift, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!cinemaScope.allCinemas()) {
                predicates.add(staffShift.get("cinemaId").in(cinemaScope.cinemaIds()));
            }
            if (staffUserId != null) {
                predicates.add(criteriaBuilder.equal(staffShift.get("staffUserId"), staffUserId));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(staffShift.<Instant>get("openedAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(staffShift.<Instant>get("openedAt"), to));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<StaffShift> staffShifts = staffShiftRepository.findAll(specification,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(staffShifts, staffShifts.getContent().stream().map(this::toShiftResponse).toList());
    }

    private ShiftReportResponse buildReport(StaffShift staffShift) {
        Instant reportEnd = reportEnd(staffShift);
        List<CounterMethodTotalResponse> methodTotals = revenueReportService.sumCounterSalesByMethod(
                staffShift.getStaffUserId(), staffShift.getOpenedAt(), reportEnd);
        List<CounterTransactionResponse> transactions = revenueReportService.findCounterTransactions(
                staffShift.getStaffUserId(), staffShift.getOpenedAt(), reportEnd);
        return new ShiftReportResponse(
                toShiftResponse(staffShift, methodTotals), methodTotals, sumCash(methodTotals), transactions);
    }

    private ShiftResponse toShiftResponse(StaffShift staffShift) {
        return toShiftResponse(staffShift, revenueReportService.sumCounterSalesByMethod(
                staffShift.getStaffUserId(), staffShift.getOpenedAt(), reportEnd(staffShift)));
    }

    private ShiftResponse toShiftResponse(StaffShift staffShift, List<CounterMethodTotalResponse> methodTotals) {
        BigDecimal totalSales = methodTotals.stream()
                .map(counterMethodTotalResponse -> counterMethodTotalResponse.bookingAmount()
                        .add(counterMethodTotalResponse.fnbAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long orderCount = methodTotals.stream()
                .mapToLong(counterMethodTotalResponse -> counterMethodTotalResponse.bookingOrders()
                        + counterMethodTotalResponse.fnbOrders())
                .sum();
        boolean isOpen = staffShift.getStatus() == ShiftStatus.OPEN;
        BigDecimal expectedCash = isOpen
                ? staffShift.getOpeningCash().add(sumCash(methodTotals))
                : staffShift.getExpectedCash();
        return new ShiftResponse(
                staffShift.getId(),
                staffShift.getStaffUserId(),
                userQueryService.findContact(staffShift.getStaffUserId())
                        .map(UserContactResponse::fullName)
                        .orElse("Nhân viên #" + staffShift.getStaffUserId()),
                staffShift.getCinemaId(),
                cinemaQueryService.findCinemaNames(List.of(staffShift.getCinemaId()))
                        .getOrDefault(staffShift.getCinemaId(), ""),
                staffShift.getStatus(),
                staffShift.getOpenedAt(),
                staffShift.getClosedAt(),
                staffShift.getOpeningCash(),
                expectedCash,
                staffShift.getCountedCash(),
                staffShift.getCashDifference(),
                staffShift.getNote(),
                totalSales,
                orderCount);
    }

    private BigDecimal findCashSales(StaffShift staffShift, Instant closedAt) {
        return sumCash(revenueReportService.sumCounterSalesByMethod(
                staffShift.getStaffUserId(), staffShift.getOpenedAt(), closedAt.plusMillis(REPORT_END_PADDING_MILLIS)));
    }

    private BigDecimal sumCash(List<CounterMethodTotalResponse> methodTotals) {
        return methodTotals.stream()
                .filter(counterMethodTotalResponse -> counterMethodTotalResponse.paymentMethod() == PaymentMethod.CASH)
                .map(counterMethodTotalResponse -> counterMethodTotalResponse.bookingAmount()
                        .add(counterMethodTotalResponse.fnbAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Instant reportEnd(StaffShift staffShift) {
        Instant end = staffShift.getClosedAt() != null ? staffShift.getClosedAt() : Instant.now(clock);
        return end.plusMillis(REPORT_END_PADDING_MILLIS);
    }
}
