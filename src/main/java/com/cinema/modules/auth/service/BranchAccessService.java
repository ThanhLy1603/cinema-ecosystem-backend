package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.response.BranchAssignmentResponse;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserBranchAssignment;
import com.cinema.modules.auth.repository.UserBranchAssignmentRepository;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.cinema.service.CinemaQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BranchAccessService {

    static final Duration CACHE_TTL = Duration.ofMinutes(5);
    static final String CACHE_KEY_PREFIX = "branch-access:";
    static final String ALL_BRANCHES_MARKER = "*";
    private static final String ID_SEPARATOR = ",";

    private final UserRepository userRepository;
    private final UserBranchAssignmentRepository userBranchAssignmentRepository;
    private final CinemaQueryService cinemaQueryService;
    private final StringRedisTemplate stringRedisTemplate;

    public BranchAccessService(
            UserRepository userRepository,
            UserBranchAssignmentRepository userBranchAssignmentRepository,
            CinemaQueryService cinemaQueryService,
            StringRedisTemplate stringRedisTemplate) {
        this.userRepository = userRepository;
        this.userBranchAssignmentRepository = userBranchAssignmentRepository;
        this.cinemaQueryService = cinemaQueryService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Set<Long> getAccessibleCinemaIds(Long userId) {
        Optional<Set<Long>> optionalAssignedCinemaIds = resolveAssignedCinemaIds(userId);
        if (optionalAssignedCinemaIds.isEmpty()) {
            return cinemaQueryService.findAllCinemaIds();
        }
        return optionalAssignedCinemaIds.get();
    }

    public boolean hasAccessToAllCinemas(Long userId) {
        return resolveAssignedCinemaIds(userId).isEmpty();
    }

    public boolean canAccessCinema(Long userId, Long cinemaId) {
        if (cinemaId == null) {
            return false;
        }
        return resolveAssignedCinemaIds(userId)
                .map(assignedCinemaIds -> assignedCinemaIds.contains(cinemaId))
                .orElse(true);
    }

    public void assertCanAccessCinema(Long userId, Long cinemaId) {
        if (!canAccessCinema(userId, cinemaId)) {
            throw new AppException("Bạn không có quyền truy cập dữ liệu của chi nhánh này", HttpStatus.FORBIDDEN,
                    "BRANCH_ACCESS_DENIED");
        }
    }

    public List<BranchAssignmentResponse> getAssignedBranches(Long userId) {
        List<UserBranchAssignment> userBranchAssignments = userBranchAssignmentRepository.findByUserId(userId);
        Set<Long> cinemaIds = userBranchAssignments.stream()
                .map(UserBranchAssignment::getCinemaId)
                .collect(Collectors.toSet());
        Map<Long, String> cinemaNames = cinemaQueryService.findCinemaNames(cinemaIds);
        return userBranchAssignments.stream()
                .map(userBranchAssignment -> new BranchAssignmentResponse(
                        userBranchAssignment.getCinemaId(),
                        cinemaNames.get(userBranchAssignment.getCinemaId()),
                        userBranchAssignment.isPrimaryBranch()))
                .sorted(Comparator.comparing(BranchAssignmentResponse::primary).reversed()
                        .thenComparing(BranchAssignmentResponse::cinemaId))
                .toList();
    }

    public void evict(Long userId) {
        try {
            stringRedisTemplate.delete(cacheKey(userId));
        } catch (RuntimeException runtimeException) {
            log.warn("Không xóa được cache phạm vi chi nhánh của user {}: {}", userId, runtimeException.getMessage());
        }
    }

    public void evictAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                evict(userId);
            }
        });
    }

    private Optional<Set<Long>> resolveAssignedCinemaIds(Long userId) {
        String cachedValue = readCache(userId);
        if (cachedValue != null) {
            try {
                return decode(cachedValue);
            } catch (NumberFormatException numberFormatException) {
                log.warn("Cache phạm vi chi nhánh của user {} bị hỏng, tải lại từ DB", userId);
            }
        }
        Optional<Set<Long>> optionalAssignedCinemaIds = loadAssignedCinemaIds(userId);
        writeCache(userId, encode(optionalAssignedCinemaIds));
        return optionalAssignedCinemaIds;
    }

    private Optional<Set<Long>> loadAssignedCinemaIds(Long userId) {
        Optional<User> optionalUser = userRepository.findWithRolesById(userId);
        if (optionalUser.isEmpty()) {
            return Optional.of(Set.of());
        }
        User user = optionalUser.get();
        if (user.hasRole(Role.ADMIN)) {
            return Optional.empty();
        }
        if (user.hasRole(Role.STAFF) || user.hasRole(Role.MANAGER)) {
            return Optional.of(Set.copyOf(userBranchAssignmentRepository.findCinemaIdsByUserId(userId)));
        }
        return Optional.of(Set.of());
    }

    private String readCache(Long userId) {
        try {
            return stringRedisTemplate.opsForValue().get(cacheKey(userId));
        } catch (RuntimeException runtimeException) {
            log.warn("Không đọc được cache phạm vi chi nhánh của user {}: {}", userId, runtimeException.getMessage());
            return null;
        }
    }

    private void writeCache(Long userId, String value) {
        try {
            stringRedisTemplate.opsForValue().set(cacheKey(userId), value, CACHE_TTL);
        } catch (RuntimeException runtimeException) {
            log.warn("Không ghi được cache phạm vi chi nhánh của user {}: {}", userId, runtimeException.getMessage());
        }
    }

    private static String encode(Optional<Set<Long>> optionalAssignedCinemaIds) {
        if (optionalAssignedCinemaIds.isEmpty()) {
            return ALL_BRANCHES_MARKER;
        }
        return optionalAssignedCinemaIds.get().stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(ID_SEPARATOR));
    }

    private static Optional<Set<Long>> decode(String cachedValue) {
        if (ALL_BRANCHES_MARKER.equals(cachedValue)) {
            return Optional.empty();
        }
        if (cachedValue.isEmpty()) {
            return Optional.of(Set.of());
        }
        return Optional.of(Arrays.stream(cachedValue.split(ID_SEPARATOR))
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet()));
    }

    private static String cacheKey(Long userId) {
        return CACHE_KEY_PREFIX + userId;
    }
}
