package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.response.BranchAssignmentResponse;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserBranchAssignment;
import com.cinema.modules.auth.repository.UserBranchAssignmentRepository;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchAccessServiceTest {

    private static final Long USER_ID = 7L;
    private static final String CACHE_KEY = "branch-access:7";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBranchAssignmentRepository userBranchAssignmentRepository;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private BranchAccessService branchAccessService;

    @BeforeEach
    void setUp() {
        branchAccessService = new BranchAccessService(
                userRepository, userBranchAssignmentRepository, cinemaQueryService, stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private User userWithRoles(String... roleCodes) {
        User user = new User();
        user.setId(USER_ID);
        for (String roleCode : roleCodes) {
            Role role = new Role();
            role.setCode(roleCode);
            user.addRole(role);
        }
        lenient().when(userRepository.findWithRolesById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    private UserBranchAssignment assignment(Long cinemaId, boolean primary) {
        UserBranchAssignment userBranchAssignment = new UserBranchAssignment();
        userBranchAssignment.setCinemaId(cinemaId);
        userBranchAssignment.setPrimaryBranch(primary);
        return userBranchAssignment;
    }

    private AppException assertDenied(Runnable runnable) {
        AppException appException = assertThrows(AppException.class, runnable::run);
        assertEquals(HttpStatus.FORBIDDEN, appException.getStatus());
        assertEquals("BRANCH_ACCESS_DENIED", appException.getErrorCode());
        return appException;
    }

    @Test
    void adminCanAccessEveryCinemaAndGetsAllCinemaIds() {
        userWithRoles(Role.ADMIN);
        when(cinemaQueryService.findAllCinemaIds()).thenReturn(Set.of(1L, 2L, 3L));

        assertTrue(branchAccessService.hasAccessToAllCinemas(USER_ID));
        assertEquals(Set.of(1L, 2L, 3L), branchAccessService.getAccessibleCinemaIds(USER_ID));
    }

    @Test
    void adminIsCachedAsAllBranchesMarker() {
        userWithRoles(Role.ADMIN);

        branchAccessService.hasAccessToAllCinemas(USER_ID);

        verify(valueOperations).set(CACHE_KEY, "*", BranchAccessService.CACHE_TTL);
    }

    @Test
    void adminPassesTheCinemaCheckWithoutListingAllCinemas() {
        userWithRoles(Role.ADMIN);

        branchAccessService.assertCanAccessCinema(USER_ID, 99L);

        verify(cinemaQueryService, never()).findAllCinemaIds();
    }

    @Test
    void staffGetsOnlyTheAssignedCinemasAndTheyAreCachedForFiveMinutes() {
        userWithRoles(Role.STAFF);
        when(userBranchAssignmentRepository.findCinemaIdsByUserId(USER_ID)).thenReturn(List.of(3L, 1L));

        assertEquals(Set.of(1L, 3L), branchAccessService.getAccessibleCinemaIds(USER_ID));
        assertFalse(branchAccessService.hasAccessToAllCinemas(USER_ID));

        verify(valueOperations, atLeastOnce()).set(CACHE_KEY, "1,3", Duration.ofMinutes(5));
        verify(cinemaQueryService, never()).findAllCinemaIds();
    }

    @Test
    void managerIsLimitedToTheAssignedCinemasToo() {
        userWithRoles(Role.MANAGER);
        when(userBranchAssignmentRepository.findCinemaIdsByUserId(USER_ID)).thenReturn(List.of(2L));

        assertEquals(Set.of(2L), branchAccessService.getAccessibleCinemaIds(USER_ID));
    }

    @Test
    void staffWithoutAnyAssignmentCanAccessNothing() {
        userWithRoles(Role.STAFF);
        when(userBranchAssignmentRepository.findCinemaIdsByUserId(USER_ID)).thenReturn(List.of());

        assertEquals(Set.of(), branchAccessService.getAccessibleCinemaIds(USER_ID));
        assertDenied(() -> branchAccessService.assertCanAccessCinema(USER_ID, 1L));
        verify(valueOperations, atLeastOnce()).set(CACHE_KEY, "", BranchAccessService.CACHE_TTL);
    }

    @Test
    void customerHasNoBranchScopeAndUnknownUsersAreDenied() {
        userWithRoles(Role.CUSTOMER);
        assertEquals(Set.of(), branchAccessService.getAccessibleCinemaIds(USER_ID));

        when(userRepository.findWithRolesById(8L)).thenReturn(Optional.empty());
        assertEquals(Set.of(), branchAccessService.getAccessibleCinemaIds(8L));
    }

    @Test
    void assertCanAccessCinemaAllowsAssignedAndRejectsOtherCinemas() {
        userWithRoles(Role.STAFF);
        when(userBranchAssignmentRepository.findCinemaIdsByUserId(USER_ID)).thenReturn(List.of(1L, 2L));

        branchAccessService.assertCanAccessCinema(USER_ID, 2L);
        assertDenied(() -> branchAccessService.assertCanAccessCinema(USER_ID, 3L));
        assertDenied(() -> branchAccessService.assertCanAccessCinema(USER_ID, null));
    }

    @Test
    void cacheHitSkipsTheDatabase() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("1,5");

        assertEquals(Set.of(1L, 5L), branchAccessService.getAccessibleCinemaIds(USER_ID));

        verifyNoInteractions(userRepository, userBranchAssignmentRepository);
    }

    @Test
    void cachedAllBranchesMarkerMeansAdminAndCachedEmptyValueMeansNoBranch() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("*");
        assertTrue(branchAccessService.hasAccessToAllCinemas(USER_ID));

        when(valueOperations.get(CACHE_KEY)).thenReturn("");
        assertEquals(Set.of(), branchAccessService.getAccessibleCinemaIds(USER_ID));

        verifyNoInteractions(userRepository);
    }

    @Test
    void corruptedCacheValueIsIgnoredAndReloadedFromTheDatabase() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("abc,1");
        userWithRoles(Role.STAFF);
        when(userBranchAssignmentRepository.findCinemaIdsByUserId(USER_ID)).thenReturn(List.of(4L));

        assertEquals(Set.of(4L), branchAccessService.getAccessibleCinemaIds(USER_ID));
        verify(valueOperations).set(CACHE_KEY, "4", BranchAccessService.CACHE_TTL);
    }

    @Test
    void redisFailuresFallBackToTheDatabaseInsteadOfBreakingTheRequest() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new IllegalStateException("redis down"));
        doThrow(new IllegalStateException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        userWithRoles(Role.STAFF);
        when(userBranchAssignmentRepository.findCinemaIdsByUserId(USER_ID)).thenReturn(List.of(6L));

        assertEquals(Set.of(6L), branchAccessService.getAccessibleCinemaIds(USER_ID));
    }

    @Test
    void evictDeletesTheCacheKeyAndSwallowsRedisErrors() {
        branchAccessService.evict(USER_ID);
        verify(stringRedisTemplate).delete(CACHE_KEY);

        when(stringRedisTemplate.delete(CACHE_KEY)).thenThrow(new IllegalStateException("redis down"));
        branchAccessService.evict(USER_ID);
    }

    @Test
    void evictAfterCommitWithoutTransactionEvictsImmediately() {
        branchAccessService.evictAfterCommit(USER_ID);

        verify(stringRedisTemplate).delete(CACHE_KEY);
    }

    @Test
    void evictAfterCommitInsideTransactionWaitsUntilTheTransactionCompletes() {
        TransactionSynchronizationManager.initSynchronization();

        branchAccessService.evictAfterCommit(USER_ID);
        verify(stringRedisTemplate, never()).delete(anyString());

        for (TransactionSynchronization transactionSynchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            transactionSynchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        verify(stringRedisTemplate).delete(CACHE_KEY);
    }

    @Test
    void assignedBranchesAreSortedWithThePrimaryBranchFirstAndCarryTheirNames() {
        when(userBranchAssignmentRepository.findByUserId(USER_ID))
                .thenReturn(List.of(assignment(1L, false), assignment(2L, true), assignment(3L, false)));
        when(cinemaQueryService.findCinemaNames(Set.of(1L, 2L, 3L)))
                .thenReturn(Map.of(1L, "CinemaSphere Q1", 2L, "CinemaSphere Q7", 3L, "CinemaSphere Thủ Đức"));

        List<BranchAssignmentResponse> branches = branchAccessService.getAssignedBranches(USER_ID);

        assertEquals(List.of(
                new BranchAssignmentResponse(2L, "CinemaSphere Q7", true),
                new BranchAssignmentResponse(1L, "CinemaSphere Q1", false),
                new BranchAssignmentResponse(3L, "CinemaSphere Thủ Đức", false)), branches);
    }
}
