package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.auth.dto.request.UpdateUserInfoRequest;
import com.cinema.modules.auth.dto.request.UpdateUserRolesRequest;
import com.cinema.modules.auth.dto.request.UpdateUserStatusRequest;
import com.cinema.modules.auth.dto.response.AdminUserResponse;
import com.cinema.modules.auth.dto.response.AuditLogResponse;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.StaffProfile;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserAuditLog;
import com.cinema.modules.auth.entity.UserBranchAssignment;
import com.cinema.modules.auth.enums.AuditAction;
import com.cinema.modules.auth.enums.StaffPosition;
import com.cinema.modules.auth.enums.StaffStatus;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.repository.RefreshTokenRepository;
import com.cinema.modules.auth.repository.RoleRepository;
import com.cinema.modules.auth.repository.StaffProfileRepository;
import com.cinema.modules.auth.repository.UserAuditLogRepository;
import com.cinema.modules.auth.repository.UserBranchAssignmentRepository;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IUserAuditService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");
    private static final Long ADMIN_ID = 1L;
    private static final Long MANAGER_ID = 2L;
    private static final Long TARGET_ID = 30L;
    private static final String IP_ADDRESS = "10.0.0.9";

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
    private UserAuditLogRepository userAuditLogRepository;

    @Mock
    private IBranchAccessService branchAccessService;

    @Mock
    private IUserAuditService userAuditService;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> detailsCaptor;

    private AdminUserService adminUserService;
    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                userRepository, staffProfileRepository, userBranchAssignmentRepository, roleRepository,
                refreshTokenRepository, userAuditLogRepository, branchAccessService, userAuditService,
                cinemaQueryService, Clock.fixed(NOW, ZoneOffset.UTC));
        adminUser = user(ADMIN_ID, UserStatus.ACTIVE, Role.ADMIN);
        lenient().when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        lenient().when(staffProfileRepository.findByUserIdIn(any())).thenReturn(List.of());
        lenient().when(userBranchAssignmentRepository.findByUserIdIn(any())).thenReturn(List.of());
        lenient().when(cinemaQueryService.findCinemaNames(any())).thenReturn(Map.of());
        lenient().when(userRepository.countByRoleCodeAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);
    }

    private Role role(String code) {
        Role role = new Role();
        role.setCode(code);
        lenient().when(roleRepository.findByCode(code)).thenReturn(Optional.of(role));
        return role;
    }

    private User user(Long id, UserStatus status, String... roleCodes) {
        User user = new User();
        user.setId(id);
        user.setEmail("u" + id + "@cinema.vn");
        user.setFullName("Người " + id);
        user.setStatus(status);
        for (String roleCode : roleCodes) {
            user.addRole(role(roleCode));
        }
        lenient().when(userRepository.findWithRolesById(id)).thenReturn(Optional.of(user));
        return user;
    }

    private User manager(Set<Long> managedCinemaIds) {
        User managerUser = user(MANAGER_ID, UserStatus.ACTIVE, Role.MANAGER);
        lenient().when(branchAccessService.getAccessibleCinemaIds(MANAGER_ID)).thenReturn(managedCinemaIds);
        return managerUser;
    }

    private void branchesOf(Long userId, Long... cinemaIds) {
        lenient().when(userBranchAssignmentRepository.findCinemaIdsByUserId(userId)).thenReturn(List.of(cinemaIds));
    }

    private AppException assertRejected(Runnable runnable, HttpStatus expectedStatus, String expectedErrorCode) {
        AppException appException = assertThrows(AppException.class, runnable::run);
        assertEquals(expectedStatus, appException.getStatus());
        assertEquals(expectedErrorCode, appException.getErrorCode());
        return appException;
    }

    private UpdateUserStatusRequest statusRequest(UserStatus status) {
        return new UpdateUserStatusRequest(status);
    }

    private UpdateUserRolesRequest rolesRequest(String roleCode) {
        return new UpdateUserRolesRequest(List.of(roleCode));
    }

    private void assertNoSideEffects() {
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
        verify(userAuditService, never()).record(any(), any(), any(), anyMap(), anyString());
    }

    @SuppressWarnings("unchecked")
    private void stubSearch(List<User> users, long total) {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(users, PageRequest.of(0, 20), total));
        when(userRepository.findWithRolesByIdIn(any())).thenReturn(users);
    }

    @Test
    void searchReturnsPageMetadataAndKeepsTheRepositoryOrderWithBranchesAndStaffProfile() {
        User first = user(10L, UserStatus.ACTIVE, Role.STAFF);
        User second = user(11L, UserStatus.ACTIVE, Role.CUSTOMER);
        stubSearch(List.of(first, second), 41);
        StaffProfile staffProfile = new StaffProfile();
        staffProfile.setUser(first);
        staffProfile.setEmployeeCode("NV010");
        staffProfile.setPosition(StaffPosition.TICKETING_STAFF);
        staffProfile.setStatus(StaffStatus.ACTIVE);
        when(staffProfileRepository.findByUserIdIn(List.of(10L, 11L))).thenReturn(List.of(staffProfile));
        UserBranchAssignment secondary = new UserBranchAssignment();
        secondary.setUser(first);
        secondary.setCinemaId(2L);
        UserBranchAssignment primary = new UserBranchAssignment();
        primary.setUser(first);
        primary.setCinemaId(1L);
        primary.setPrimaryBranch(true);
        when(userBranchAssignmentRepository.findByUserIdIn(List.of(10L, 11L))).thenReturn(List.of(secondary, primary));
        when(cinemaQueryService.findCinemaNames(Set.of(1L, 2L))).thenReturn(Map.of(1L, "Q1", 2L, "Q7"));

        PageResponse<AdminUserResponse> pageResponse =
                adminUserService.searchUsers(ADMIN_ID, "an", null, null, null, 0, 20);

        assertEquals(41, pageResponse.totalElements());
        assertEquals(3, pageResponse.totalPages());
        assertEquals(List.of(10L, 11L), pageResponse.content().stream().map(AdminUserResponse::id).toList());
        AdminUserResponse staffRow = pageResponse.content().get(0);
        assertEquals("NV010", staffRow.employeeCode());
        assertEquals(List.of(1L, 2L), staffRow.branches().stream().map(branch -> branch.cinemaId()).toList());
        assertTrue(staffRow.branches().get(0).primary());
        assertEquals("Q1", staffRow.branches().get(0).cinemaName());
        assertNull(pageResponse.content().get(1).employeeCode());
        assertTrue(pageResponse.content().get(1).branches().isEmpty());
    }

    @Test
    void searchClampsPageAndSize() {
        stubSearch(List.of(), 0);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        adminUserService.searchUsers(ADMIN_ID, null, null, null, null, -5, 5000);
        adminUserService.searchUsers(ADMIN_ID, null, null, null, null, 2, 0);

        verify(userRepository, times(2)).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getAllValues().get(0).getPageNumber());
        assertEquals(AdminUserService.MAX_PAGE_SIZE, pageableCaptor.getAllValues().get(0).getPageSize());
        assertEquals(2, pageableCaptor.getAllValues().get(1).getPageNumber());
        assertEquals(20, pageableCaptor.getAllValues().get(1).getPageSize());
    }

    @Test
    void searchAsManagerUsesTheManagedBranchesAndRejectsOtherRoles() {
        manager(Set.of(1L));
        stubSearch(List.of(), 0);

        adminUserService.searchUsers(MANAGER_ID, null, "ROLE_ADMIN", null, null, 0, 20);

        verify(branchAccessService).getAccessibleCinemaIds(MANAGER_ID);

        user(40L, UserStatus.ACTIVE, Role.STAFF);
        assertRejected(() -> adminUserService.searchUsers(40L, null, null, null, null, 0, 20),
                HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        user(41L, UserStatus.ACTIVE, Role.CUSTOMER);
        assertRejected(() -> adminUserService.searchUsers(41L, null, null, null, null, 0, 20),
                HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void suspendedOrUnknownActorsAreRejected() {
        user(42L, UserStatus.SUSPENDED, Role.ADMIN);
        assertRejected(() -> adminUserService.getUser(42L, TARGET_ID), HttpStatus.FORBIDDEN, "ACCESS_DENIED");

        when(userRepository.findWithRolesById(43L)).thenReturn(Optional.empty());
        assertRejected(() -> adminUserService.getUser(43L, TARGET_ID), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void getUserReturnsAnyUserToAdminsAndNotFoundForMissingOnes() {
        user(TARGET_ID, UserStatus.DELETED, Role.CUSTOMER);

        AdminUserResponse adminUserResponse = adminUserService.getUser(ADMIN_ID, TARGET_ID);

        assertEquals("DELETED", adminUserResponse.status());
        assertEquals(List.of("ROLE_CUSTOMER"), adminUserResponse.roles());

        when(userRepository.findWithRolesById(99L)).thenReturn(Optional.empty());
        assertRejected(() -> adminUserService.getUser(ADMIN_ID, 99L), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @Test
    void managerOnlySeesPlainStaffSharingOneOfTheirBranches() {
        manager(Set.of(1L));
        user(TARGET_ID, UserStatus.ACTIVE, Role.STAFF);
        branchesOf(TARGET_ID, 1L, 2L);
        assertEquals(TARGET_ID, adminUserService.getUser(MANAGER_ID, TARGET_ID).id());

        branchesOf(TARGET_ID, 2L);
        assertRejected(() -> adminUserService.getUser(MANAGER_ID, TARGET_ID), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");

        user(31L, UserStatus.ACTIVE, Role.MANAGER);
        branchesOf(31L, 1L);
        assertRejected(() -> adminUserService.getUser(MANAGER_ID, 31L), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");

        user(32L, UserStatus.ACTIVE, Role.ADMIN);
        branchesOf(32L, 1L);
        assertRejected(() -> adminUserService.getUser(MANAGER_ID, 32L), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");

        user(33L, UserStatus.ACTIVE, Role.CUSTOMER);
        assertRejected(() -> adminUserService.getUser(MANAGER_ID, 33L), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @Test
    void suspendingAUserRevokesSessionsAndWritesTheAuditLog() {
        User target = user(TARGET_ID, UserStatus.ACTIVE, Role.CUSTOMER);

        AdminUserResponse adminUserResponse =
                adminUserService.updateStatus(ADMIN_ID, TARGET_ID, statusRequest(UserStatus.SUSPENDED), IP_ADDRESS);

        assertEquals(UserStatus.SUSPENDED, target.getStatus());
        assertEquals("SUSPENDED", adminUserResponse.status());
        verify(refreshTokenRepository).revokeAllByUserId(TARGET_ID, NOW);
        verify(userAuditService).record(ADMIN_ID, TARGET_ID, AuditAction.STATUS_CHANGED,
                Map.of("before", "ACTIVE", "after", "SUSPENDED"), IP_ADDRESS);
    }

    @Test
    void unlockingATemporarilyLockedUserClearsTheCountersWithoutRevokingSessions() {
        User target = user(TARGET_ID, UserStatus.LOCKED, Role.CUSTOMER);
        target.setFailedLoginAttempts(5);
        target.setLockedUntil(NOW.plusSeconds(600));

        adminUserService.updateStatus(ADMIN_ID, TARGET_ID, statusRequest(UserStatus.ACTIVE), IP_ADDRESS);

        assertEquals(UserStatus.ACTIVE, target.getStatus());
        assertEquals(0, target.getFailedLoginAttempts());
        assertNull(target.getLockedUntil());
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
        verify(userAuditService).record(ADMIN_ID, TARGET_ID, AuditAction.STATUS_CHANGED,
                Map.of("before", "LOCKED", "after", "ACTIVE"), IP_ADDRESS);
    }

    @Test
    void sendingTheCurrentStatusAgainChangesNothing() {
        user(TARGET_ID, UserStatus.SUSPENDED, Role.CUSTOMER);

        adminUserService.updateStatus(ADMIN_ID, TARGET_ID, statusRequest(UserStatus.SUSPENDED), IP_ADDRESS);

        assertNoSideEffects();
    }

    @Test
    void statusCanOnlyBecomeActiveOrSuspended() {
        user(TARGET_ID, UserStatus.ACTIVE, Role.CUSTOMER);

        assertRejected(() -> adminUserService.updateStatus(
                ADMIN_ID, TARGET_ID, statusRequest(UserStatus.DELETED), IP_ADDRESS),
                HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION");
        assertRejected(() -> adminUserService.updateStatus(
                ADMIN_ID, TARGET_ID, statusRequest(UserStatus.LOCKED), IP_ADDRESS),
                HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION");
        assertNoSideEffects();
    }

    @Test
    void deletedUsersCannotBeChangedAnymore() {
        user(TARGET_ID, UserStatus.DELETED, Role.CUSTOMER);

        assertRejected(() -> adminUserService.updateStatus(
                ADMIN_ID, TARGET_ID, statusRequest(UserStatus.ACTIVE), IP_ADDRESS), HttpStatus.CONFLICT, "USER_DELETED");
        assertRejected(() -> adminUserService.updateRoles(ADMIN_ID, TARGET_ID, rolesRequest(Role.ADMIN), IP_ADDRESS),
                HttpStatus.CONFLICT, "USER_DELETED");
        assertRejected(() -> adminUserService.deleteUser(ADMIN_ID, TARGET_ID, IP_ADDRESS),
                HttpStatus.CONFLICT, "USER_DELETED");
        assertNoSideEffects();
    }

    @Test
    void anAdminCannotLockDeleteOrDowngradeThemselves() {
        assertRejected(() -> adminUserService.updateStatus(
                ADMIN_ID, ADMIN_ID, statusRequest(UserStatus.SUSPENDED), IP_ADDRESS),
                HttpStatus.FORBIDDEN, "CANNOT_MODIFY_SELF");
        assertRejected(() -> adminUserService.updateRoles(ADMIN_ID, ADMIN_ID, rolesRequest(Role.CUSTOMER), IP_ADDRESS),
                HttpStatus.FORBIDDEN, "CANNOT_MODIFY_SELF");
        assertRejected(() -> adminUserService.deleteUser(ADMIN_ID, ADMIN_ID, IP_ADDRESS),
                HttpStatus.FORBIDDEN, "CANNOT_MODIFY_SELF");
        assertNoSideEffects();
    }

    @Test
    void keepingYourOwnAdminRoleIsAHarmlessNoOp() {
        adminUserService.updateRoles(ADMIN_ID, ADMIN_ID, rolesRequest(Role.ADMIN), IP_ADDRESS);

        assertNoSideEffects();
    }

    @Test
    void theLastActiveAdminCanNeverBeSuspendedDeletedOrDemoted() {
        user(TARGET_ID, UserStatus.ACTIVE, Role.ADMIN);
        when(userRepository.countByRoleCodeAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertRejected(() -> adminUserService.updateStatus(
                ADMIN_ID, TARGET_ID, statusRequest(UserStatus.SUSPENDED), IP_ADDRESS),
                HttpStatus.CONFLICT, "LAST_ADMIN");
        assertRejected(() -> adminUserService.updateRoles(ADMIN_ID, TARGET_ID, rolesRequest(Role.CUSTOMER), IP_ADDRESS),
                HttpStatus.CONFLICT, "LAST_ADMIN");
        assertRejected(() -> adminUserService.deleteUser(ADMIN_ID, TARGET_ID, IP_ADDRESS),
                HttpStatus.CONFLICT, "LAST_ADMIN");
        assertNoSideEffects();
    }

    @Test
    void anAdminCanBeSuspendedWhenAnotherActiveAdminRemains() {
        User target = user(TARGET_ID, UserStatus.ACTIVE, Role.ADMIN);

        adminUserService.updateStatus(ADMIN_ID, TARGET_ID, statusRequest(UserStatus.SUSPENDED), IP_ADDRESS);

        assertEquals(UserStatus.SUSPENDED, target.getStatus());
    }

    @Test
    void managerCanSuspendStaffOfTheirBranchButNotOthers() {
        manager(Set.of(1L));
        User target = user(TARGET_ID, UserStatus.ACTIVE, Role.STAFF);
        branchesOf(TARGET_ID, 1L);

        adminUserService.updateStatus(MANAGER_ID, TARGET_ID, statusRequest(UserStatus.SUSPENDED), IP_ADDRESS);

        assertEquals(UserStatus.SUSPENDED, target.getStatus());
        verify(userAuditService).record(eq(MANAGER_ID), eq(TARGET_ID), eq(AuditAction.STATUS_CHANGED), anyMap(),
                eq(IP_ADDRESS));

        user(34L, UserStatus.ACTIVE, Role.STAFF);
        branchesOf(34L, 2L);
        assertRejected(() -> adminUserService.updateStatus(
                MANAGER_ID, 34L, statusRequest(UserStatus.SUSPENDED), IP_ADDRESS),
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @Test
    void changingARoleReplacesItRevokesSessionsAuditsAndClearsTheBranchCache() {
        User target = user(TARGET_ID, UserStatus.ACTIVE, Role.CUSTOMER);

        AdminUserResponse adminUserResponse =
                adminUserService.updateRoles(ADMIN_ID, TARGET_ID, rolesRequest(Role.ADMIN), IP_ADDRESS);

        assertTrue(target.hasRole(Role.ADMIN));
        assertFalse(target.hasRole(Role.CUSTOMER));
        assertEquals(List.of("ROLE_ADMIN"), adminUserResponse.roles());
        verify(refreshTokenRepository).revokeAllByUserId(TARGET_ID, NOW);
        verify(userAuditService).record(ADMIN_ID, TARGET_ID, AuditAction.ROLES_CHANGED,
                Map.of("before", List.of("ROLE_CUSTOMER"), "after", List.of("ROLE_ADMIN")), IP_ADDRESS);
        verify(branchAccessService).evictAfterCommit(TARGET_ID);
    }

    @Test
    void assigningTheSameRoleAgainChangesNothing() {
        user(TARGET_ID, UserStatus.ACTIVE, Role.CUSTOMER);

        adminUserService.updateRoles(ADMIN_ID, TARGET_ID, rolesRequest(Role.CUSTOMER), IP_ADDRESS);

        assertNoSideEffects();
        verify(branchAccessService, never()).evictAfterCommit(any());
    }

    @Test
    void becomingStaffOrManagerRequiresAStaffProfileAndAtLeastOneBranch() {
        user(TARGET_ID, UserStatus.ACTIVE, Role.CUSTOMER);

        assertRejected(() -> adminUserService.updateRoles(ADMIN_ID, TARGET_ID, rolesRequest(Role.STAFF), IP_ADDRESS),
                HttpStatus.BAD_REQUEST, "STAFF_PROFILE_REQUIRED");

        when(staffProfileRepository.findByUserId(TARGET_ID)).thenReturn(Optional.of(new StaffProfile()));
        assertRejected(() -> adminUserService.updateRoles(ADMIN_ID, TARGET_ID, rolesRequest(Role.MANAGER), IP_ADDRESS),
                HttpStatus.BAD_REQUEST, "STAFF_PROFILE_REQUIRED");
        assertNoSideEffects();

        branchesOf(TARGET_ID, 1L);
        role(Role.MANAGER);
        adminUserService.updateRoles(ADMIN_ID, TARGET_ID, rolesRequest(Role.MANAGER), IP_ADDRESS);
        verify(refreshTokenRepository).revokeAllByUserId(TARGET_ID, NOW);
    }

    @Test
    void managersCannotChangeRolesDeleteOrReadAuditLogs() {
        manager(Set.of(1L));
        user(TARGET_ID, UserStatus.ACTIVE, Role.STAFF);
        branchesOf(TARGET_ID, 1L);

        assertRejected(() -> adminUserService.updateRoles(MANAGER_ID, TARGET_ID, rolesRequest(Role.MANAGER), IP_ADDRESS),
                HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertRejected(() -> adminUserService.deleteUser(MANAGER_ID, TARGET_ID, IP_ADDRESS),
                HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertRejected(() -> adminUserService.getAuditLogs(MANAGER_ID, TARGET_ID, 0, 20),
                HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertNoSideEffects();
    }

    @Test
    void deletingIsASoftDeleteThatRevokesSessionsAndIsAudited() {
        User target = user(TARGET_ID, UserStatus.ACTIVE, Role.CUSTOMER);

        adminUserService.deleteUser(ADMIN_ID, TARGET_ID, IP_ADDRESS);

        assertEquals(UserStatus.DELETED, target.getStatus());
        verify(userRepository, never()).delete(any(User.class));
        verify(refreshTokenRepository).revokeAllByUserId(TARGET_ID, NOW);
        verify(userAuditService).record(ADMIN_ID, TARGET_ID, AuditAction.USER_DELETED,
                Map.of("email", "u30@cinema.vn", "previousStatus", "ACTIVE"), IP_ADDRESS);
        verify(branchAccessService).evictAfterCommit(TARGET_ID);
    }

    @Test
    void updateUserInfoChangesNameAndPhoneAndRecordsBeforeAndAfter() {
        User target = user(TARGET_ID, UserStatus.ACTIVE, Role.CUSTOMER);
        target.setPhone("0911111111");

        AdminUserResponse adminUserResponse = adminUserService.updateUserInfo(
                ADMIN_ID, TARGET_ID, new UpdateUserInfoRequest("  Trần Mới  ", "0922222222"), IP_ADDRESS);

        assertEquals("Trần Mới", target.getFullName());
        assertEquals("0922222222", adminUserResponse.phone());
        verify(userAuditService).record(eq(ADMIN_ID), eq(TARGET_ID), eq(AuditAction.USER_UPDATED),
                detailsCaptor.capture(), eq(IP_ADDRESS));
        assertEquals(Map.of("fullName", "Người 30", "phone", "0911111111"), detailsCaptor.getValue().get("before"));
        assertEquals(Map.of("fullName", "Trần Mới", "phone", "0922222222"), detailsCaptor.getValue().get("after"));
    }

    @Test
    void updateUserInfoWithoutChangesWritesNothing() {
        User target = user(TARGET_ID, UserStatus.ACTIVE, Role.CUSTOMER);

        adminUserService.updateUserInfo(
                ADMIN_ID, TARGET_ID, new UpdateUserInfoRequest(target.getFullName(), null), IP_ADDRESS);

        assertNoSideEffects();
    }

    @Test
    void updateUserInfoRejectsAPhoneUsedByAnotherAccount() {
        user(TARGET_ID, UserStatus.ACTIVE, Role.CUSTOMER);
        when(userRepository.existsByPhoneAndIdNot("0933333333", TARGET_ID)).thenReturn(true);

        assertRejected(() -> adminUserService.updateUserInfo(
                ADMIN_ID, TARGET_ID, new UpdateUserInfoRequest("Tên", "0933333333"), IP_ADDRESS),
                HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS");

        when(userRepository.existsByPhoneAndIdNot("0944444444", TARGET_ID)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("race"));
        assertRejected(() -> adminUserService.updateUserInfo(
                ADMIN_ID, TARGET_ID, new UpdateUserInfoRequest("Tên", "0944444444"), IP_ADDRESS),
                HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS");
    }

    @Test
    void auditLogsAreReturnedNewestFirstWithTheActorName() {
        user(TARGET_ID, UserStatus.ACTIVE, Role.CUSTOMER);
        UserAuditLog userAuditLog = new UserAuditLog();
        userAuditLog.setId(7L);
        userAuditLog.setActorId(ADMIN_ID);
        userAuditLog.setTargetUserId(TARGET_ID);
        userAuditLog.setAction(AuditAction.STATUS_CHANGED);
        userAuditLog.setDetails(Map.of("before", "ACTIVE", "after", "SUSPENDED"));
        userAuditLog.setCreatedAt(NOW);
        UserAuditLog systemLog = new UserAuditLog();
        systemLog.setId(6L);
        systemLog.setTargetUserId(TARGET_ID);
        systemLog.setAction(AuditAction.USER_CREATED);
        when(userAuditLogRepository.findByTargetUserIdOrderByCreatedAtDesc(eq(TARGET_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(userAuditLog, systemLog), PageRequest.of(0, 20), 2));
        when(userRepository.findAllById(Set.of(ADMIN_ID))).thenReturn(List.of(adminUser));

        PageResponse<AuditLogResponse> pageResponse = adminUserService.getAuditLogs(ADMIN_ID, TARGET_ID, 0, 20);

        assertEquals(2, pageResponse.totalElements());
        assertEquals("Người 1", pageResponse.content().get(0).actorName());
        assertEquals("STATUS_CHANGED", pageResponse.content().get(0).action());
        assertNull(pageResponse.content().get(1).actorName());
    }

    @Test
    void auditLogsOfAnUnknownUserAreNotFound() {
        when(userRepository.findWithRolesById(99L)).thenReturn(Optional.empty());

        assertRejected(() -> adminUserService.getAuditLogs(ADMIN_ID, 99L, 0, 20),
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }
}
