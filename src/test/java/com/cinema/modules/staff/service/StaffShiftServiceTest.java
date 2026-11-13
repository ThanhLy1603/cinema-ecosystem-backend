package com.cinema.modules.staff.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.scope.CinemaScopeResolver;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.payment.dto.response.CounterMethodTotalResponse;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.service.interfaces.IRevenueReportService;
import com.cinema.modules.staff.dto.request.CloseShiftRequest;
import com.cinema.modules.staff.dto.request.OpenShiftRequest;
import com.cinema.modules.staff.dto.response.ShiftReportResponse;
import com.cinema.modules.staff.entity.StaffShift;
import com.cinema.modules.staff.enums.ShiftStatus;
import com.cinema.modules.staff.repository.StaffShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffShiftServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final Instant OPENED_AT = Instant.parse("2026-11-13T02:00:00Z");
    private static final Long STAFF_USER_ID = 3L;
    private static final Long CINEMA_ID = 1L;

    @Mock
    private StaffShiftRepository staffShiftRepository;

    @Mock
    private IRevenueReportService revenueReportService;

    @Mock
    private IBranchAccessService branchAccessService;

    @Mock
    private CinemaScopeResolver cinemaScopeResolver;

    @Mock
    private IUserQueryService userQueryService;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    @Mock
    private IAuditLogService auditLogService;

    private StaffShiftService staffShiftService;

    @BeforeEach
    void setUp() {
        staffShiftService = new StaffShiftService(
                staffShiftRepository,
                revenueReportService,
                branchAccessService,
                cinemaScopeResolver,
                userQueryService,
                cinemaQueryService,
                auditLogService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(userQueryService.findContact(STAFF_USER_ID))
                .thenReturn(Optional.of(new UserContactResponse(STAFF_USER_ID, "an@cinema.vn", "Nguyễn Văn An")));
        lenient().when(cinemaQueryService.findCinemaNames(anyCollection())).thenReturn(Map.of(CINEMA_ID, "Star"));
        lenient().when(staffShiftRepository.save(any(StaffShift.class))).thenAnswer(invocation -> {
            StaffShift staffShift = invocation.getArgument(0);
            if (staffShift.getId() == null) {
                staffShift.setId(9L);
            }
            return staffShift;
        });
        lenient().when(revenueReportService.sumCounterSalesByMethod(eq(STAFF_USER_ID), any(), any()))
                .thenReturn(List.of(
                        new CounterMethodTotalResponse(
                                PaymentMethod.CASH, 2, new BigDecimal("300000"), 1, new BigDecimal("40000")),
                        new CounterMethodTotalResponse(
                                PaymentMethod.CARD, 1, new BigDecimal("100000"), 0, BigDecimal.ZERO)));
        lenient().when(revenueReportService.findCounterTransactions(eq(STAFF_USER_ID), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void openShiftStoresOpeningCashAndAudits() {
        when(staffShiftRepository.findFirstByStaffUserIdAndStatus(STAFF_USER_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.empty());

        ShiftReportResponse shiftReportResponse = staffShiftService.openShift(
                STAFF_USER_ID, new OpenShiftRequest(CINEMA_ID, new BigDecimal("500000")));

        assertEquals(ShiftStatus.OPEN, shiftReportResponse.shift().status());
        assertEquals(new BigDecimal("500000"), shiftReportResponse.shift().openingCash());
        verify(branchAccessService).assertCanAccessCinema(STAFF_USER_ID, CINEMA_ID);
        verify(auditLogService).record(
                eq(STAFF_USER_ID), eq(CINEMA_ID), eq(AuditAction.SHIFT_OPENED), eq("SHIFT"), eq("9"), any());
    }

    @Test
    void openShiftRejectsSecondOpenShift() {
        when(staffShiftRepository.findFirstByStaffUserIdAndStatus(STAFF_USER_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.of(new StaffShift()));

        AppException appException = assertThrows(AppException.class, () -> staffShiftService.openShift(
                STAFF_USER_ID, new OpenShiftRequest(CINEMA_ID, BigDecimal.ZERO)));

        assertEquals("SHIFT_ALREADY_OPEN", appException.getErrorCode());
        verify(staffShiftRepository, never()).save(any());
    }

    @Test
    void currentShiftExpectedCashAddsOnlyCashSalesToOpeningCash() {
        when(staffShiftRepository.findFirstByStaffUserIdAndStatus(STAFF_USER_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.of(openShift()));

        ShiftReportResponse shiftReportResponse = staffShiftService.findCurrentShift(STAFF_USER_ID);

        assertEquals(new BigDecimal("340000"), shiftReportResponse.cashSales());
        assertEquals(new BigDecimal("840000"), shiftReportResponse.shift().expectedCash());
        assertEquals(new BigDecimal("440000"), shiftReportResponse.shift().totalSales());
        assertEquals(4, shiftReportResponse.shift().orderCount());
    }

    @Test
    void currentShiftIsNullWhenNothingOpen() {
        when(staffShiftRepository.findFirstByStaffUserIdAndStatus(STAFF_USER_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.empty());

        assertNull(staffShiftService.findCurrentShift(STAFF_USER_ID));
    }

    @Test
    void closeShiftComputesCashDifferenceAndAudits() {
        when(staffShiftRepository.findByStaffUserIdAndStatusForUpdate(STAFF_USER_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.of(openShift()));

        ShiftReportResponse shiftReportResponse = staffShiftService.closeShift(
                STAFF_USER_ID, new CloseShiftRequest(new BigDecimal("835000"), " thiếu 5k "));

        assertEquals(ShiftStatus.CLOSED, shiftReportResponse.shift().status());
        assertEquals(new BigDecimal("840000"), shiftReportResponse.shift().expectedCash());
        assertEquals(new BigDecimal("-5000"), shiftReportResponse.shift().cashDifference());
        assertEquals("thiếu 5k", shiftReportResponse.shift().note());
        ArgumentCaptor<StaffShift> staffShiftCaptor = ArgumentCaptor.forClass(StaffShift.class);
        verify(staffShiftRepository).save(staffShiftCaptor.capture());
        assertEquals(NOW, staffShiftCaptor.getValue().getClosedAt());
        verify(auditLogService).record(
                eq(STAFF_USER_ID), eq(CINEMA_ID), eq(AuditAction.SHIFT_CLOSED), eq("SHIFT"), eq("9"), any());
    }

    @Test
    void closeShiftFailsWhenNoOpenShift() {
        when(staffShiftRepository.findByStaffUserIdAndStatusForUpdate(STAFF_USER_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.empty());

        AppException appException = assertThrows(AppException.class, () -> staffShiftService.closeShift(
                STAFF_USER_ID, new CloseShiftRequest(BigDecimal.ZERO, null)));

        assertEquals("NO_OPEN_SHIFT", appException.getErrorCode());
    }

    @Test
    void shiftReportOfAnotherStaffRequiresBranchAccess() {
        StaffShift staffShift = openShift();
        staffShift.setStaffUserId(77L);
        when(staffShiftRepository.findById(9L)).thenReturn(Optional.of(staffShift));
        lenient().when(revenueReportService.sumCounterSalesByMethod(eq(77L), any(), any())).thenReturn(List.of());
        lenient().when(revenueReportService.findCounterTransactions(eq(77L), any(), any())).thenReturn(List.of());
        lenient().when(userQueryService.findContact(77L)).thenReturn(Optional.empty());

        staffShiftService.findShiftReport(STAFF_USER_ID, 9L);

        verify(branchAccessService).assertCanAccessCinema(STAFF_USER_ID, CINEMA_ID);
    }

    private StaffShift openShift() {
        StaffShift staffShift = new StaffShift();
        staffShift.setId(9L);
        staffShift.setStaffUserId(STAFF_USER_ID);
        staffShift.setCinemaId(CINEMA_ID);
        staffShift.setStatus(ShiftStatus.OPEN);
        staffShift.setOpenedAt(OPENED_AT);
        staffShift.setOpeningCash(new BigDecimal("500000"));
        return staffShift;
    }
}
