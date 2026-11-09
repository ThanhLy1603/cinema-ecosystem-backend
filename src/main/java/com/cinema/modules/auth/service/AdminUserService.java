package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.auth.dto.request.UpdateUserInfoRequest;
import com.cinema.modules.auth.dto.request.UpdateUserRolesRequest;
import com.cinema.modules.auth.dto.request.UpdateUserStatusRequest;
import com.cinema.modules.auth.dto.response.AdminUserResponse;
import com.cinema.modules.auth.dto.response.AuditLogResponse;
import com.cinema.modules.auth.dto.response.BranchAssignmentResponse;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.StaffProfile;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserAuditLog;
import com.cinema.modules.auth.entity.UserBranchAssignment;
import com.cinema.modules.auth.enums.AuditAction;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.repository.RefreshTokenRepository;
import com.cinema.modules.auth.repository.RoleRepository;
import com.cinema.modules.auth.repository.StaffProfileRepository;
import com.cinema.modules.auth.repository.UserAuditLogRepository;
import com.cinema.modules.auth.repository.UserBranchAssignmentRepository;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.auth.specification.UserSpecifications;
import com.cinema.modules.auth.service.interfaces.IAdminUserService;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IUserAuditService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminUserService implements IAdminUserService {

    static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final UserRepository userRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final UserBranchAssignmentRepository userBranchAssignmentRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAuditLogRepository userAuditLogRepository;
    private final IBranchAccessService branchAccessService;
    private final IUserAuditService userAuditService;
    private final ICinemaQueryService cinemaQueryService;
    private final Clock clock;

    public AdminUserService(
            UserRepository userRepository,
            StaffProfileRepository staffProfileRepository,
            UserBranchAssignmentRepository userBranchAssignmentRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserAuditLogRepository userAuditLogRepository,
            IBranchAccessService branchAccessService,
            IUserAuditService userAuditService,
            ICinemaQueryService cinemaQueryService,
            Clock clock) {
        this.userRepository = userRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.userBranchAssignmentRepository = userBranchAssignmentRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userAuditLogRepository = userAuditLogRepository;
        this.branchAccessService = branchAccessService;
        this.userAuditService = userAuditService;
        this.cinemaQueryService = cinemaQueryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<AdminUserResponse> searchUsers(
            Long actorUserId, String keyword, String roleCode, UserStatus status, Long cinemaId, int page, int size) {
        Actor actor = loadActor(actorUserId);
        String effectiveRoleCode = actor.admin() ? roleCode : Role.STAFF;
        Set<Long> cinemaIds = resolveCinemaFilter(actor, cinemaId);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<User> userPage =
                userRepository.findAll(UserSpecifications.search(keyword, effectiveRoleCode, status, cinemaIds), pageable);
        List<Long> userIds = userPage.getContent().stream().map(User::getId).toList();
        Map<Long, User> usersWithRoles = userRepository.findWithRolesByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<User> orderedUsers = userIds.stream().map(usersWithRoles::get).toList();
        return PageResponse.of(userPage, toResponses(orderedUsers));
    }

    @Transactional(readOnly = true)
    @Override
    public AdminUserResponse getUser(Long actorUserId, Long targetUserId) {
        Actor actor = loadActor(actorUserId);
        return toResponse(loadVisibleTarget(actor, targetUserId));
    }

    @Transactional
    @Override
    public AdminUserResponse updateUserInfo(
            Long actorUserId, Long targetUserId, UpdateUserInfoRequest updateUserInfoRequest, String ipAddress) {
        Actor actor = loadActor(actorUserId);
        User target = loadVisibleTarget(actor, targetUserId);
        ensureNotDeleted(target);

        String fullName = updateUserInfoRequest.fullName().trim();
        String phone = normalizePhone(updateUserInfoRequest.phone());
        if (phone != null && userRepository.existsByPhoneAndIdNot(phone, targetUserId)) {
            throw phoneAlreadyUsed();
        }
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        trackChange(before, after, "fullName", target.getFullName(), fullName);
        trackChange(before, after, "phone", target.getPhone(), phone);
        if (after.isEmpty()) {
            return toResponse(target);
        }
        target.setFullName(fullName);
        target.setPhone(phone);
        try {
            userRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw phoneAlreadyUsed();
        }
        userAuditService.record(actorUserId, targetUserId, AuditAction.USER_UPDATED,
                Map.of("before", before, "after", after), ipAddress);
        return toResponse(target);
    }

    @Transactional
    @Override
    public AdminUserResponse updateStatus(
            Long actorUserId, Long targetUserId, UpdateUserStatusRequest updateUserStatusRequest, String ipAddress) {
        Actor actor = loadActor(actorUserId);
        User target = loadVisibleTarget(actor, targetUserId);
        UserStatus newStatus = updateUserStatusRequest.status();
        if (newStatus != UserStatus.ACTIVE && newStatus != UserStatus.SUSPENDED) {
            throw new AppException("Chỉ có thể chuyển tài khoản sang ACTIVE (mở khóa) hoặc SUSPENDED (khóa)",
                    HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION");
        }
        ensureNotDeleted(target);
        ensureNotSelf(actorUserId, targetUserId);
        UserStatus previousStatus = target.getStatus();
        if (previousStatus == newStatus) {
            return toResponse(target);
        }
        if (newStatus == UserStatus.SUSPENDED) {
            ensureNotLastActiveAdmin(target);
        }

        target.setStatus(newStatus);
        if (newStatus == UserStatus.ACTIVE) {
            target.setFailedLoginAttempts(0);
            target.setLockedUntil(null);
        }
        userRepository.saveAndFlush(target);
        if (newStatus == UserStatus.SUSPENDED) {
            refreshTokenRepository.revokeAllByUserId(targetUserId, clock.instant());
        }
        userAuditService.record(actorUserId, targetUserId, AuditAction.STATUS_CHANGED,
                Map.of("before", previousStatus.name(), "after", newStatus.name()), ipAddress);
        return toResponse(target);
    }

    @Transactional
    @Override
    public AdminUserResponse updateRoles(
            Long actorUserId, Long targetUserId, UpdateUserRolesRequest updateUserRolesRequest, String ipAddress) {
        Actor actor = loadActor(actorUserId);
        ensureAdmin(actor);
        User target = loadVisibleTarget(actor, targetUserId);
        ensureNotDeleted(target);

        String newRoleCode = updateUserRolesRequest.roles().get(0);
        List<String> previousRoleCodes = target.getRoles().stream().map(Role::getCode).sorted().toList();
        if (previousRoleCodes.equals(List.of(newRoleCode))) {
            return toResponse(target);
        }
        ensureNotSelf(actorUserId, targetUserId);
        if (target.hasRole(Role.ADMIN)) {
            ensureNotLastActiveAdmin(target);
        }
        boolean needsStaffProfile = Role.STAFF.equals(newRoleCode) || Role.MANAGER.equals(newRoleCode);
        if (needsStaffProfile && !hasStaffProfileAndBranch(targetUserId)) {
            throw new AppException(
                    "Tài khoản chưa có hồ sơ nhân viên và chi nhánh, hãy tạo nhân viên qua màn hình quản lý nhân viên",
                    HttpStatus.BAD_REQUEST, "STAFF_PROFILE_REQUIRED");
        }
        Role newRole = roleRepository.findByCode(newRoleCode)
                .orElseThrow(() -> new IllegalStateException("Thiếu role " + newRoleCode + " trong bảng roles"));

        target.getRoles().clear();
        target.addRole(newRole);
        userRepository.saveAndFlush(target);
        refreshTokenRepository.revokeAllByUserId(targetUserId, clock.instant());
        userAuditService.record(actorUserId, targetUserId, AuditAction.ROLES_CHANGED,
                Map.of("before", previousRoleCodes, "after", List.of(newRoleCode)), ipAddress);
        branchAccessService.evictAfterCommit(targetUserId);
        return toResponse(target);
    }

    @Transactional
    @Override
    public void deleteUser(Long actorUserId, Long targetUserId, String ipAddress) {
        Actor actor = loadActor(actorUserId);
        ensureAdmin(actor);
        User target = loadVisibleTarget(actor, targetUserId);
        ensureNotDeleted(target);
        ensureNotSelf(actorUserId, targetUserId);
        ensureNotLastActiveAdmin(target);

        UserStatus previousStatus = target.getStatus();
        target.setStatus(UserStatus.DELETED);
        userRepository.saveAndFlush(target);
        refreshTokenRepository.revokeAllByUserId(targetUserId, clock.instant());
        userAuditService.record(actorUserId, targetUserId, AuditAction.USER_DELETED,
                Map.of("email", target.getEmail(), "previousStatus", previousStatus.name()), ipAddress);
        branchAccessService.evictAfterCommit(targetUserId);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<AuditLogResponse> getAuditLogs(Long actorUserId, Long targetUserId, int page, int size) {
        Actor actor = loadActor(actorUserId);
        ensureAdmin(actor);
        loadVisibleTarget(actor, targetUserId);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0), size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE));
        Page<UserAuditLog> userAuditLogPage =
                userAuditLogRepository.findByTargetUserIdOrderByCreatedAtDesc(targetUserId, pageable);

        Set<Long> actorIds = userAuditLogPage.getContent().stream()
                .map(UserAuditLog::getActorId)
                .filter(actorId -> actorId != null)
                .collect(Collectors.toSet());
        Map<Long, String> actorNames = new HashMap<>();
        userRepository.findAllById(actorIds).forEach(user -> actorNames.put(user.getId(), user.getFullName()));
        List<AuditLogResponse> auditLogResponses = userAuditLogPage.getContent().stream()
                .map(userAuditLog -> AuditLogResponse.from(userAuditLog, actorNames.get(userAuditLog.getActorId())))
                .toList();
        return PageResponse.of(userAuditLogPage, auditLogResponses);
    }

    private Actor loadActor(Long actorUserId) {
        User actorUser = userRepository.findWithRolesById(actorUserId).orElseThrow(AdminUserService::forbidden);
        if (actorUser.getStatus() != UserStatus.ACTIVE) {
            throw forbidden();
        }
        if (actorUser.hasRole(Role.ADMIN)) {
            return new Actor(actorUserId, true, Set.of());
        }
        if (actorUser.hasRole(Role.MANAGER)) {
            return new Actor(actorUserId, false, branchAccessService.getAccessibleCinemaIds(actorUserId));
        }
        throw forbidden();
    }

    private Set<Long> resolveCinemaFilter(Actor actor, Long cinemaId) {
        if (actor.admin()) {
            return cinemaId == null ? null : Set.of(cinemaId);
        }
        if (cinemaId == null) {
            return actor.managedCinemaIds();
        }
        return actor.managedCinemaIds().contains(cinemaId) ? Set.of(cinemaId) : Set.of();
    }

    private User loadVisibleTarget(Actor actor, Long targetUserId) {
        User target = userRepository.findWithRolesById(targetUserId).orElseThrow(AdminUserService::userNotFound);
        if (actor.admin()) {
            return target;
        }
        boolean plainStaff = target.hasRole(Role.STAFF) && !target.hasRole(Role.MANAGER) && !target.hasRole(Role.ADMIN);
        List<Long> targetCinemaIds = userBranchAssignmentRepository.findCinemaIdsByUserId(targetUserId);
        if (!plainStaff || Collections.disjoint(actor.managedCinemaIds(), targetCinemaIds)) {
            throw userNotFound();
        }
        return target;
    }

    private boolean hasStaffProfileAndBranch(Long userId) {
        return staffProfileRepository.findByUserId(userId).isPresent()
                && !userBranchAssignmentRepository.findCinemaIdsByUserId(userId).isEmpty();
    }

    private void ensureAdmin(Actor actor) {
        if (!actor.admin()) {
            throw forbidden();
        }
    }

    private void ensureNotDeleted(User target) {
        if (target.getStatus() == UserStatus.DELETED) {
            throw new AppException("Tài khoản đã bị xóa", HttpStatus.CONFLICT, "USER_DELETED");
        }
    }

    private void ensureNotSelf(Long actorUserId, Long targetUserId) {
        if (actorUserId.equals(targetUserId)) {
            throw new AppException("Bạn không thể tự khóa, xóa hoặc đổi quyền của chính mình", HttpStatus.FORBIDDEN,
                    "CANNOT_MODIFY_SELF");
        }
    }

    private void ensureNotLastActiveAdmin(User target) {
        if (target.hasRole(Role.ADMIN) && target.getStatus() == UserStatus.ACTIVE
                && userRepository.countByRoleCodeAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new AppException("Hệ thống phải luôn còn ít nhất một Admin đang hoạt động", HttpStatus.CONFLICT,
                    "LAST_ADMIN");
        }
    }

    private AdminUserResponse toResponse(User user) {
        return toResponses(List.of(user)).get(0);
    }

    private List<AdminUserResponse> toResponses(List<User> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = users.stream().map(User::getId).toList();
        Map<Long, StaffProfile> staffProfiles = staffProfileRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(staffProfile -> staffProfile.getUser().getId(), Function.identity()));
        List<UserBranchAssignment> userBranchAssignments = userBranchAssignmentRepository.findByUserIdIn(userIds);
        Set<Long> cinemaIds = userBranchAssignments.stream()
                .map(UserBranchAssignment::getCinemaId)
                .collect(Collectors.toSet());
        Map<Long, String> cinemaNames = cinemaQueryService.findCinemaNames(cinemaIds);

        Map<Long, List<BranchAssignmentResponse>> branchesByUserId = new HashMap<>();
        for (UserBranchAssignment userBranchAssignment : userBranchAssignments) {
            branchesByUserId
                    .computeIfAbsent(userBranchAssignment.getUser().getId(), userId -> new ArrayList<>())
                    .add(new BranchAssignmentResponse(
                            userBranchAssignment.getCinemaId(),
                            cinemaNames.get(userBranchAssignment.getCinemaId()),
                            userBranchAssignment.isPrimaryBranch()));
        }
        Comparator<BranchAssignmentResponse> primaryFirst = Comparator
                .comparing(BranchAssignmentResponse::primary).reversed()
                .thenComparing(BranchAssignmentResponse::cinemaId);
        return users.stream()
                .map(user -> AdminUserResponse.from(
                        user,
                        staffProfiles.get(user.getId()),
                        branchesByUserId.getOrDefault(user.getId(), List.of()).stream().sorted(primaryFirst).toList()))
                .toList();
    }

    private static void trackChange(
            Map<String, Object> before, Map<String, Object> after, String field, String oldValue, String newValue) {
        boolean unchanged = oldValue == null ? newValue == null : oldValue.equals(newValue);
        if (!unchanged) {
            before.put(field, oldValue == null ? "" : oldValue);
            after.put(field, newValue == null ? "" : newValue);
        }
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }

    private static AppException phoneAlreadyUsed() {
        return new AppException("Số điện thoại đã được sử dụng", HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS");
    }

    private static AppException userNotFound() {
        return new AppException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    private static AppException forbidden() {
        return new AppException("Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    private record Actor(Long userId, boolean admin, Set<Long> managedCinemaIds) {
    }
}
