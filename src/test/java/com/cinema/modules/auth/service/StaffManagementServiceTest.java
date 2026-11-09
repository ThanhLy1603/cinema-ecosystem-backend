package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.request.AssignBranchesRequest;
import com.cinema.modules.auth.dto.request.CreateStaffRequest;
import com.cinema.modules.auth.dto.request.UpdateStaffRequest;
import com.cinema.modules.auth.dto.response.BranchAssignmentResponse;
import com.cinema.modules.auth.dto.response.StaffResponse;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.StaffProfile;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserBranchAssignment;
import com.cinema.modules.auth.enums.AuditAction;
import com.cinema.modules.auth.enums.StaffPosition;
import com.cinema.modules.auth.enums.StaffStatus;
import com.cinema.modules.auth.repository.RefreshTokenRepository;
import com.cinema.modules.auth.repository.RoleRepository;
import com.cinema.modules.auth.repository.StaffProfileRepository;
import com.cinema.modules.auth.repository.UserBranchAssignmentRepository;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.cinema.service.CinemaQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");
    private static final Long ACTOR_ID = 1L;
    private static final Long USER_ID = 20L;
    private static final String IP_ADDRESS = "10.0.0.5";

    @Mock
    private UserRepository userRepository;

    @Mock
    private StaffProfileRepository staffProfileRepository;

    @Mock
    private UserBranchAssignmentRepository userBranchAssignmentRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private CinemaQueryService cinemaQueryService;

    @Mock
    private BranchAccessService branchAccessService;

    @Mock
    private UserAuditService userAuditService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Captor
    private ArgumentCaptor<List<UserBranchAssignment>> assignmentsCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> detailsCaptor;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private StaffManagementService staffManagementService;
    private Role staffRole;
    private Role managerRole;
    private Role customerRole;
    private User actorUser;

    @BeforeEach
    void setUp() {
        staffManagementService = new StaffManagementService(
                userRepository, staffProfileRepository, userBranchAssignmentRepository, roleRepository,
                refreshTokenRepository, cinemaQueryService, branchAccessService, userAuditService,
                emailVerificationService, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
        staffRole = role(Role.STAFF);
        managerRole = role(Role.MANAGER);
        customerRole = role(Role.CUSTOMER);
        actorUser = new User();
        actorUser.setId(ACTOR_ID);
        lenient().when(roleRepository.findByCode(Role.STAFF)).thenReturn(Optional.of(staffRole));
        lenient().when(roleRepository.findByCode(Role.MANAGER)).thenReturn(Optional.of(managerRole));
        lenient().when(userRepository.getReferenceById(ACTOR_ID)).thenReturn(actorUser);
        lenient().when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocationOnMock -> {
            User savedUser = invocationOnMock.getArgument(0);
            if (savedUser.getId() == null) {
                savedUser.setId(USER_ID);
            }
            return savedUser;
        });
        lenient().when(staffProfileRepository.saveAndFlush(any(StaffProfile.class)))
                .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        lenient().when(cinemaQueryService.findActiveCinemaIds(any())).thenAnswer(invocationOnMock -> {
            Collection<Long> requestedCinemaIds = invocationOnMock.getArgument(0);
            return Set.copyOf(requestedCinemaIds);
        });
        lenient().when(branchAccessService.getAssignedBranches(USER_ID))
                .thenReturn(List.of(new BranchAssignmentResponse(1L, "CinemaSphere Q1", true)));
    }

    private Role role(String code) {
        Role role = new Role();
        role.setCode(code);
        return role;
    }

    private User existingStaff(Role... roles) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("nv@cinema.vn");
        user.setFullName("Nhân Viên");
        for (Role role : roles) {
            user.addRole(role);
        }
        lenient().when(userRepository.findWithRolesById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    private StaffProfile existingProfile() {
        StaffProfile staffProfile = new StaffProfile();
        staffProfile.setId(5L);
        staffProfile.setEmployeeCode("NV001");
        staffProfile.setPosition(StaffPosition.TICKETING_STAFF);
        staffProfile.setStatus(StaffStatus.ACTIVE);
        staffProfile.setHiredAt(LocalDate.of(2026, 1, 15));
        lenient().when(staffProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(staffProfile));
        return staffProfile;
    }

    private UserBranchAssignment assignment(Long cinemaId, boolean primary) {
        UserBranchAssignment userBranchAssignment = new UserBranchAssignment();
        userBranchAssignment.setCinemaId(cinemaId);
        userBranchAssignment.setPrimaryBranch(primary);
        return userBranchAssignment;
    }

    private CreateStaffRequest createRequest(List<Long> cinemaIds, Long primaryCinemaId) {
        return new CreateStaffRequest(
                "  NV@Cinema.VN ", "Matkhau123", "  Nhân Viên  ", "0912345678", Role.STAFF, " NV001 ",
                StaffPosition.TICKETING_STAFF, LocalDate.of(2026, 1, 15), cinemaIds, primaryCinemaId);
    }

    private AppException assertRejected(Runnable runnable, HttpStatus expectedStatus, String expectedErrorCode) {
        AppException appException = assertThrows(AppException.class, runnable::run);
        assertEquals(expectedStatus, appException.getStatus());
        assertEquals(expectedErrorCode, appException.getErrorCode());
        return appException;
    }

    private void assertNothingPersisted() {
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(staffProfileRepository, never()).saveAndFlush(any(StaffProfile.class));
        verify(userBranchAssignmentRepository, never()).saveAll(any());
        verify(branchAccessService, never()).evictAfterCommit(any());
    }

    @Test
    void createStaffStoresUserProfileAndAssignmentsAndAuditsTheAction() {
        existingStaff(staffRole);
        existingProfile();

        StaffResponse staffResponse =
                staffManagementService.createStaff(ACTOR_ID, createRequest(List.of(3L, 1L), null), IP_ADDRESS);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("nv@cinema.vn", savedUser.getEmail());
        assertEquals("Nhân Viên", savedUser.getFullName());
        assertFalse(savedUser.isEmailVerified());
        assertTrue(savedUser.hasRole(Role.STAFF));
        assertNotEquals("Matkhau123", savedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches("Matkhau123", savedUser.getPasswordHash()));

        ArgumentCaptor<StaffProfile> profileCaptor = ArgumentCaptor.forClass(StaffProfile.class);
        verify(staffProfileRepository).saveAndFlush(profileCaptor.capture());
        assertEquals("NV001", profileCaptor.getValue().getEmployeeCode());
        assertEquals(StaffStatus.ACTIVE, profileCaptor.getValue().getStatus());
        assertSame(savedUser, profileCaptor.getValue().getUser());

        verify(userBranchAssignmentRepository).saveAll(assignmentsCaptor.capture());
        List<UserBranchAssignment> userBranchAssignments = assignmentsCaptor.getValue();
        assertEquals(List.of(3L, 1L), userBranchAssignments.stream().map(UserBranchAssignment::getCinemaId).toList());
        assertEquals(List.of(true, false),
                userBranchAssignments.stream().map(UserBranchAssignment::isPrimaryBranch).toList());
        assertSame(actorUser, userBranchAssignments.get(0).getAssignedBy());

        verify(userAuditService).record(eq(ACTOR_ID), eq(USER_ID), eq(AuditAction.USER_CREATED),
                detailsCaptor.capture(), eq(IP_ADDRESS));
        assertEquals(Role.STAFF, detailsCaptor.getValue().get("role"));
        assertEquals(List.of(3L, 1L), detailsCaptor.getValue().get("cinemaIds"));
        verify(emailVerificationService).sendVerification(USER_ID, IP_ADDRESS);
        verify(branchAccessService).evictAfterCommit(USER_ID);
        assertEquals(USER_ID, staffResponse.userId());
        assertEquals(1, staffResponse.branches().size());
    }

    @Test
    void createStaffHonoursAnExplicitPrimaryBranch() {
        existingStaff(staffRole);
        existingProfile();

        staffManagementService.createStaff(ACTOR_ID, createRequest(List.of(3L, 1L), 1L), IP_ADDRESS);

        verify(userBranchAssignmentRepository).saveAll(assignmentsCaptor.capture());
        List<UserBranchAssignment> userBranchAssignments = assignmentsCaptor.getValue();
        assertEquals(List.of(false, true),
                userBranchAssignments.stream().map(UserBranchAssignment::isPrimaryBranch).toList());
    }

    @Test
    void createStaffRemovesDuplicatedCinemaIdsAndKeepsASinglePrimary() {
        existingStaff(staffRole);
        existingProfile();

        staffManagementService.createStaff(ACTOR_ID, createRequest(List.of(2L, 2L, 2L), null), IP_ADDRESS);

        verify(userBranchAssignmentRepository).saveAll(assignmentsCaptor.capture());
        assertEquals(1, assignmentsCaptor.getValue().size());
        assertTrue(assignmentsCaptor.getValue().get(0).isPrimaryBranch());
    }

    @Test
    void createStaffRejectsAPrimaryBranchThatIsNotInTheAssignedList() {
        assertRejected(() -> staffManagementService.createStaff(
                ACTOR_ID, createRequest(List.of(1L, 2L), 9L), IP_ADDRESS),
                HttpStatus.BAD_REQUEST, "PRIMARY_BRANCH_NOT_ASSIGNED");

        assertNothingPersisted();
    }

    @Test
    void createStaffRejectsMissingOrInactiveBranches() {
        doReturn(Set.of(1L)).when(cinemaQueryService).findActiveCinemaIds(any());

        AppException appException = assertRejected(() -> staffManagementService.createStaff(
                ACTOR_ID, createRequest(List.of(1L, 8L), null), IP_ADDRESS),
                HttpStatus.BAD_REQUEST, "BRANCH_NOT_AVAILABLE");

        assertTrue(appException.getMessage().contains("8"));
        assertNothingPersisted();
    }

    @Test
    void createStaffRejectsDuplicateEmailPhoneAndEmployeeCode() {
        when(userRepository.existsByEmail("nv@cinema.vn")).thenReturn(true);
        assertRejected(() -> staffManagementService.createStaff(
                ACTOR_ID, createRequest(List.of(1L), null), IP_ADDRESS), HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");

        when(userRepository.existsByEmail("nv@cinema.vn")).thenReturn(false);
        when(userRepository.existsByPhone("0912345678")).thenReturn(true);
        assertRejected(() -> staffManagementService.createStaff(
                ACTOR_ID, createRequest(List.of(1L), null), IP_ADDRESS), HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS");

        when(userRepository.existsByPhone("0912345678")).thenReturn(false);
        when(staffProfileRepository.existsByEmployeeCode("NV001")).thenReturn(true);
        assertRejected(() -> staffManagementService.createStaff(
                ACTOR_ID, createRequest(List.of(1L), null), IP_ADDRESS),
                HttpStatus.CONFLICT, "EMPLOYEE_CODE_ALREADY_EXISTS");

        assertNothingPersisted();
    }

    @Test
    void createStaffTurnsARaceOnUniqueConstraintsIntoConflict() {
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertRejected(() -> staffManagementService.createStaff(
                ACTOR_ID, createRequest(List.of(1L), null), IP_ADDRESS), HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS");

        verify(userBranchAssignmentRepository, never()).saveAll(any());
    }

    @Test
    void createStaffRejectsPasswordsLongerThanBcryptAllows() {
        CreateStaffRequest createStaffRequest = new CreateStaffRequest(
                "nv@cinema.vn", "Mk1" + "é".repeat(40), "Nhân Viên", null, Role.STAFF, "NV001",
                StaffPosition.AUDITOR, null, List.of(1L), null);

        assertRejected(() -> staffManagementService.createStaff(ACTOR_ID, createStaffRequest, IP_ADDRESS),
                HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG");
        assertNothingPersisted();
    }

    @Test
    void getStaffReturnsNotFoundForAccountsThatAreNotStaffOrManager() {
        existingStaff(customerRole);

        assertRejected(() -> staffManagementService.getStaff(USER_ID), HttpStatus.NOT_FOUND, "STAFF_NOT_FOUND");

        when(userRepository.findWithRolesById(99L)).thenReturn(Optional.empty());
        assertRejected(() -> staffManagementService.getStaff(99L), HttpStatus.NOT_FOUND, "STAFF_NOT_FOUND");
    }

    @Test
    void updateStaffChangesTheProfileAndRecordsBeforeAndAfter() {
        existingStaff(staffRole);
        StaffProfile staffProfile = existingProfile();
        UpdateStaffRequest updateStaffRequest = new UpdateStaffRequest(
                Role.STAFF, "NV002", StaffPosition.AUDITOR, StaffStatus.ON_LEAVE, LocalDate.of(2026, 2, 1));

        staffManagementService.updateStaff(ACTOR_ID, USER_ID, updateStaffRequest, IP_ADDRESS);

        assertEquals("NV002", staffProfile.getEmployeeCode());
        assertEquals(StaffPosition.AUDITOR, staffProfile.getPosition());
        assertEquals(StaffStatus.ON_LEAVE, staffProfile.getStatus());
        verify(userAuditService).record(eq(ACTOR_ID), eq(USER_ID), eq(AuditAction.USER_UPDATED),
                detailsCaptor.capture(), eq(IP_ADDRESS));
        Map<String, Object> before = castMap(detailsCaptor.getValue().get("before"));
        Map<String, Object> after = castMap(detailsCaptor.getValue().get("after"));
        assertEquals("NV001", before.get("employeeCode"));
        assertEquals("NV002", after.get("employeeCode"));
        assertEquals("ACTIVE", before.get("status"));
        assertEquals("ON_LEAVE", after.get("status"));
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
        verify(branchAccessService, never()).evictAfterCommit(any());
    }

    @Test
    void updateStaffWithoutRealChangesWritesNoAuditEntry() {
        existingStaff(staffRole);
        existingProfile();
        UpdateStaffRequest updateStaffRequest = new UpdateStaffRequest(
                Role.STAFF, "NV001", StaffPosition.TICKETING_STAFF, StaffStatus.ACTIVE, LocalDate.of(2026, 1, 15));

        staffManagementService.updateStaff(ACTOR_ID, USER_ID, updateStaffRequest, IP_ADDRESS);

        verify(userAuditService, never()).record(any(), any(), any(), anyMap(), anyString());
    }

    @Test
    void updateStaffRejectsAnEmployeeCodeUsedByAnotherProfile() {
        existingStaff(staffRole);
        existingProfile();
        when(staffProfileRepository.existsByEmployeeCodeAndIdNot("NV999", 5L)).thenReturn(true);
        UpdateStaffRequest updateStaffRequest = new UpdateStaffRequest(
                Role.STAFF, "NV999", StaffPosition.TICKETING_STAFF, StaffStatus.ACTIVE, null);

        assertRejected(() -> staffManagementService.updateStaff(ACTOR_ID, USER_ID, updateStaffRequest, IP_ADDRESS),
                HttpStatus.CONFLICT, "EMPLOYEE_CODE_ALREADY_EXISTS");
        verify(staffProfileRepository, never()).saveAndFlush(any(StaffProfile.class));
    }

    @Test
    void updateStaffChangingTheRoleRevokesSessionsAuditsAndClearsTheBranchCache() {
        User user = existingStaff(staffRole);
        existingProfile();
        UpdateStaffRequest updateStaffRequest = new UpdateStaffRequest(
                Role.MANAGER, "NV001", StaffPosition.MANAGER, StaffStatus.ACTIVE, LocalDate.of(2026, 1, 15));

        staffManagementService.updateStaff(ACTOR_ID, USER_ID, updateStaffRequest, IP_ADDRESS);

        assertTrue(user.hasRole(Role.MANAGER));
        assertFalse(user.hasRole(Role.STAFF));
        verify(refreshTokenRepository).revokeAllByUserId(USER_ID, NOW);
        verify(userAuditService).record(eq(ACTOR_ID), eq(USER_ID), eq(AuditAction.ROLES_CHANGED),
                eq(Map.of("before", Role.STAFF, "after", Role.MANAGER)), eq(IP_ADDRESS));
        verify(branchAccessService).evictAfterCommit(USER_ID);
    }

    @Test
    void assignBranchesReplacesTheAssignmentsAndClearsTheCache() {
        existingStaff(managerRole);
        existingProfile();
        when(userBranchAssignmentRepository.findByUserId(USER_ID))
                .thenReturn(List.of(assignment(1L, true), assignment(2L, false)));

        staffManagementService.assignBranches(
                ACTOR_ID, USER_ID, new AssignBranchesRequest(List.of(2L, 3L), 3L), IP_ADDRESS);

        verify(userBranchAssignmentRepository).deleteByUserId(USER_ID);
        verify(userBranchAssignmentRepository).saveAll(assignmentsCaptor.capture());
        List<UserBranchAssignment> userBranchAssignments = assignmentsCaptor.getValue();
        assertEquals(List.of(2L, 3L), userBranchAssignments.stream().map(UserBranchAssignment::getCinemaId).toList());
        assertEquals(List.of(false, true),
                userBranchAssignments.stream().map(UserBranchAssignment::isPrimaryBranch).toList());
        verify(userAuditService).record(eq(ACTOR_ID), eq(USER_ID), eq(AuditAction.BRANCHES_CHANGED),
                detailsCaptor.capture(), eq(IP_ADDRESS));
        assertEquals(List.of(1L, 2L), detailsCaptor.getValue().get("beforeCinemaIds"));
        assertEquals(1L, detailsCaptor.getValue().get("beforePrimaryCinemaId"));
        assertEquals(List.of(2L, 3L), detailsCaptor.getValue().get("afterCinemaIds"));
        assertEquals(3L, detailsCaptor.getValue().get("afterPrimaryCinemaId"));
        verify(branchAccessService).evictAfterCommit(USER_ID);
    }

    @Test
    void assignBranchesWithTheSameSelectionChangesNothing() {
        existingStaff(staffRole);
        existingProfile();
        when(userBranchAssignmentRepository.findByUserId(USER_ID))
                .thenReturn(List.of(assignment(1L, true), assignment(2L, false)));

        staffManagementService.assignBranches(
                ACTOR_ID, USER_ID, new AssignBranchesRequest(List.of(2L, 1L), 1L), IP_ADDRESS);

        verify(userBranchAssignmentRepository, never()).deleteByUserId(any());
        verify(userBranchAssignmentRepository, never()).saveAll(any());
        verify(userAuditService, never()).record(any(), any(), any(), anyMap(), anyString());
        verify(branchAccessService, never()).evictAfterCommit(any());
    }

    @Test
    void assignBranchesRejectsInactiveBranchesAndLeavesTheCurrentAssignmentsUntouched() {
        existingStaff(staffRole);
        doReturn(Set.of()).when(cinemaQueryService).findActiveCinemaIds(any());

        assertRejected(() -> staffManagementService.assignBranches(
                ACTOR_ID, USER_ID, new AssignBranchesRequest(List.of(7L), null), IP_ADDRESS),
                HttpStatus.BAD_REQUEST, "BRANCH_NOT_AVAILABLE");

        verify(userBranchAssignmentRepository, never()).deleteByUserId(any());
        verify(branchAccessService, never()).evictAfterCommit(any());
    }

    @Test
    void assignBranchesRejectsAccountsThatAreNotStaffOrManager() {
        existingStaff(customerRole);

        assertRejected(() -> staffManagementService.assignBranches(
                ACTOR_ID, USER_ID, new AssignBranchesRequest(List.of(1L), null), IP_ADDRESS),
                HttpStatus.NOT_FOUND, "STAFF_NOT_FOUND");
        verify(userBranchAssignmentRepository, never()).deleteByUserId(any());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
