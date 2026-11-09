package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.request.AssignBranchesRequest;
import com.cinema.modules.auth.dto.request.CreateStaffRequest;
import com.cinema.modules.auth.dto.request.UpdateStaffRequest;
import com.cinema.modules.auth.dto.response.StaffResponse;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.StaffProfile;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserBranchAssignment;
import com.cinema.modules.auth.enums.AuditAction;
import com.cinema.modules.auth.enums.StaffStatus;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.repository.RefreshTokenRepository;
import com.cinema.modules.auth.repository.RoleRepository;
import com.cinema.modules.auth.repository.StaffProfileRepository;
import com.cinema.modules.auth.repository.UserBranchAssignmentRepository;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.auth.service.interfaces.IStaffManagementService;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IEmailVerificationService;
import com.cinema.modules.auth.service.interfaces.IUserAuditService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StaffManagementService implements IStaffManagementService {

    private final UserRepository userRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final UserBranchAssignmentRepository userBranchAssignmentRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ICinemaQueryService cinemaQueryService;
    private final IBranchAccessService branchAccessService;
    private final IUserAuditService userAuditService;
    private final IEmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public StaffManagementService(
            UserRepository userRepository,
            StaffProfileRepository staffProfileRepository,
            UserBranchAssignmentRepository userBranchAssignmentRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            ICinemaQueryService cinemaQueryService,
            IBranchAccessService branchAccessService,
            IUserAuditService userAuditService,
            IEmailVerificationService emailVerificationService,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.userRepository = userRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.userBranchAssignmentRepository = userBranchAssignmentRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cinemaQueryService = cinemaQueryService;
        this.branchAccessService = branchAccessService;
        this.userAuditService = userAuditService;
        this.emailVerificationService = emailVerificationService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    @Override
    public StaffResponse createStaff(Long actorUserId, CreateStaffRequest createStaffRequest, String ipAddress) {
        authorizeCreation(actorUserId, createStaffRequest.role(), new LinkedHashSet<>(createStaffRequest.cinemaIds()));
        String email = createStaffRequest.email().trim().toLowerCase(Locale.ROOT);
        String phone = normalizePhone(createStaffRequest.phone());
        String employeeCode = createStaffRequest.employeeCode().trim();
        if (userRepository.existsByEmail(email)) {
            throw new AppException("Email đã được sử dụng", HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new AppException("Số điện thoại đã được sử dụng", HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS");
        }
        if (staffProfileRepository.existsByEmployeeCode(employeeCode)) {
            throw employeeCodeAlreadyUsed();
        }
        AuthService.ensurePasswordFitsBcrypt(createStaffRequest.password());
        BranchSelection branchSelection =
                resolveBranchSelection(createStaffRequest.cinemaIds(), createStaffRequest.primaryCinemaId());
        Role role = loadRole(createStaffRequest.role());

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(createStaffRequest.password()));
        user.setEmailVerified(false);
        user.setFullName(createStaffRequest.fullName().trim());
        user.setPhone(phone);
        user.addRole(role);

        StaffProfile staffProfile = new StaffProfile();
        staffProfile.setEmployeeCode(employeeCode);
        staffProfile.setPosition(createStaffRequest.position());
        staffProfile.setStatus(StaffStatus.ACTIVE);
        staffProfile.setHiredAt(createStaffRequest.hiredAt());

        try {
            userRepository.saveAndFlush(user);
            staffProfile.setUser(user);
            staffProfileRepository.saveAndFlush(staffProfile);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw new AppException("Tài khoản hoặc mã nhân viên đã tồn tại", HttpStatus.CONFLICT,
                    "ACCOUNT_ALREADY_EXISTS");
        }
        insertAssignments(user, actorUserId, branchSelection);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("role", role.getCode());
        details.put("employeeCode", employeeCode);
        details.put("position", staffProfile.getPosition().name());
        details.put("cinemaIds", new ArrayList<>(branchSelection.cinemaIds()));
        details.put("primaryCinemaId", branchSelection.primaryCinemaId());
        userAuditService.record(actorUserId, user.getId(), AuditAction.USER_CREATED, details, ipAddress);

        emailVerificationService.sendVerification(user.getId(), ipAddress);
        branchAccessService.evictAfterCommit(user.getId());
        return buildResponse(user.getId());
    }

    @Transactional(readOnly = true)
    @Override
    public StaffResponse getStaff(Long userId) {
        loadStaffUser(userId);
        return buildResponse(userId);
    }

    @Transactional
    @Override
    public StaffResponse updateStaff(
            Long actorUserId, Long userId, UpdateStaffRequest updateStaffRequest, String ipAddress) {
        User user = loadStaffUser(userId);
        StaffProfile staffProfile = loadStaffProfile(userId);
        String employeeCode = updateStaffRequest.employeeCode().trim();
        if (!employeeCode.equals(staffProfile.getEmployeeCode())
                && staffProfileRepository.existsByEmployeeCodeAndIdNot(employeeCode, staffProfile.getId())) {
            throw employeeCodeAlreadyUsed();
        }

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        trackChange(before, after, "employeeCode", staffProfile.getEmployeeCode(), employeeCode);
        trackChange(before, after, "position", staffProfile.getPosition().name(),
                updateStaffRequest.position().name());
        trackChange(before, after, "status", staffProfile.getStatus().name(), updateStaffRequest.status().name());
        trackChange(before, after, "hiredAt", String.valueOf(staffProfile.getHiredAt()),
                String.valueOf(updateStaffRequest.hiredAt()));

        staffProfile.setEmployeeCode(employeeCode);
        staffProfile.setPosition(updateStaffRequest.position());
        staffProfile.setStatus(updateStaffRequest.status());
        staffProfile.setHiredAt(updateStaffRequest.hiredAt());
        try {
            staffProfileRepository.saveAndFlush(staffProfile);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw employeeCodeAlreadyUsed();
        }
        if (!after.isEmpty()) {
            userAuditService.record(actorUserId, userId, AuditAction.USER_UPDATED,
                    Map.of("before", before, "after", after), ipAddress);
        }

        String previousRoleCode = user.hasRole(Role.MANAGER) ? Role.MANAGER : Role.STAFF;
        if (!previousRoleCode.equals(updateStaffRequest.role())) {
            changeRole(user, updateStaffRequest.role());
            refreshTokenRepository.revokeAllByUserId(userId, clock.instant());
            userAuditService.record(actorUserId, userId, AuditAction.ROLES_CHANGED,
                    Map.of("before", previousRoleCode, "after", updateStaffRequest.role()), ipAddress);
            branchAccessService.evictAfterCommit(userId);
        }
        return buildResponse(userId);
    }

    @Transactional
    @Override
    public StaffResponse assignBranches(
            Long actorUserId, Long userId, AssignBranchesRequest assignBranchesRequest, String ipAddress) {
        User user = loadStaffUser(userId);
        BranchSelection branchSelection =
                resolveBranchSelection(assignBranchesRequest.cinemaIds(), assignBranchesRequest.primaryCinemaId());

        List<UserBranchAssignment> currentAssignments = userBranchAssignmentRepository.findByUserId(userId);
        Set<Long> currentCinemaIds = currentAssignments.stream()
                .map(UserBranchAssignment::getCinemaId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Long currentPrimaryCinemaId = currentAssignments.stream()
                .filter(UserBranchAssignment::isPrimaryBranch)
                .map(UserBranchAssignment::getCinemaId)
                .findFirst()
                .orElse(null);
        boolean unchanged = currentCinemaIds.equals(new HashSet<>(branchSelection.cinemaIds()))
                && branchSelection.primaryCinemaId().equals(currentPrimaryCinemaId);
        if (unchanged) {
            return buildResponse(userId);
        }

        userBranchAssignmentRepository.deleteByUserId(userId);
        insertAssignments(userRepository.getReferenceById(user.getId()), actorUserId, branchSelection);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("beforeCinemaIds", new ArrayList<>(currentCinemaIds));
        details.put("beforePrimaryCinemaId", currentPrimaryCinemaId);
        details.put("afterCinemaIds", new ArrayList<>(branchSelection.cinemaIds()));
        details.put("afterPrimaryCinemaId", branchSelection.primaryCinemaId());
        userAuditService.record(actorUserId, userId, AuditAction.BRANCHES_CHANGED, details, ipAddress);

        branchAccessService.evictAfterCommit(userId);
        return buildResponse(userId);
    }

    private void authorizeCreation(Long actorUserId, String roleCode, Set<Long> requestedCinemaIds) {
        User actorUser = userRepository.findWithRolesById(actorUserId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(StaffManagementService::accessDenied);
        if (actorUser.hasRole(Role.ADMIN)) {
            return;
        }
        if (!actorUser.hasRole(Role.MANAGER)) {
            throw accessDenied();
        }
        if (!Role.STAFF.equals(roleCode)) {
            throw new AppException("Quản lý chi nhánh chỉ được tạo tài khoản nhân viên (ROLE_STAFF)",
                    HttpStatus.FORBIDDEN, "ROLE_ASSIGNMENT_FORBIDDEN");
        }
        if (!branchAccessService.getAccessibleCinemaIds(actorUserId).containsAll(requestedCinemaIds)) {
            throw new AppException("Bạn không có quyền truy cập dữ liệu của chi nhánh này", HttpStatus.FORBIDDEN,
                    "BRANCH_ACCESS_DENIED");
        }
    }

    private void insertAssignments(User user, Long actorUserId, BranchSelection branchSelection) {
        User actorUser = userRepository.getReferenceById(actorUserId);
        List<UserBranchAssignment> userBranchAssignments = new ArrayList<>();
        for (Long cinemaId : branchSelection.cinemaIds()) {
            UserBranchAssignment userBranchAssignment = new UserBranchAssignment();
            userBranchAssignment.setUser(user);
            userBranchAssignment.setCinemaId(cinemaId);
            userBranchAssignment.setPrimaryBranch(cinemaId.equals(branchSelection.primaryCinemaId()));
            userBranchAssignment.setAssignedBy(actorUser);
            userBranchAssignments.add(userBranchAssignment);
        }
        userBranchAssignmentRepository.saveAll(userBranchAssignments);
        userBranchAssignmentRepository.flush();
    }

    private BranchSelection resolveBranchSelection(List<Long> requestedCinemaIds, Long requestedPrimaryCinemaId) {
        Set<Long> cinemaIds = new LinkedHashSet<>(requestedCinemaIds);
        Long primaryCinemaId = requestedPrimaryCinemaId == null ? cinemaIds.iterator().next() : requestedPrimaryCinemaId;
        if (!cinemaIds.contains(primaryCinemaId)) {
            throw new AppException("Chi nhánh chính phải nằm trong danh sách chi nhánh được gán",
                    HttpStatus.BAD_REQUEST, "PRIMARY_BRANCH_NOT_ASSIGNED");
        }
        Set<Long> activeCinemaIds = cinemaQueryService.findActiveCinemaIds(cinemaIds);
        List<Long> unavailableCinemaIds = cinemaIds.stream()
                .filter(cinemaId -> !activeCinemaIds.contains(cinemaId))
                .toList();
        if (!unavailableCinemaIds.isEmpty()) {
            throw new AppException("Chi nhánh không tồn tại hoặc không hoạt động: " + unavailableCinemaIds,
                    HttpStatus.BAD_REQUEST, "BRANCH_NOT_AVAILABLE");
        }
        return new BranchSelection(cinemaIds, primaryCinemaId);
    }

    private User loadStaffUser(Long userId) {
        User user = userRepository.findWithRolesById(userId).orElseThrow(StaffManagementService::staffNotFound);
        if (!user.hasRole(Role.STAFF) && !user.hasRole(Role.MANAGER)) {
            throw staffNotFound();
        }
        return user;
    }

    private StaffProfile loadStaffProfile(Long userId) {
        return staffProfileRepository.findByUserId(userId).orElseThrow(StaffManagementService::staffNotFound);
    }

    private Role loadRole(String roleCode) {
        return roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Thiếu role " + roleCode + " trong bảng roles"));
    }

    private void changeRole(User user, String newRoleCode) {
        user.getRoles().removeIf(role -> Role.STAFF.equals(role.getCode()) || Role.MANAGER.equals(role.getCode()));
        user.addRole(loadRole(newRoleCode));
        userRepository.saveAndFlush(user);
    }

    private StaffResponse buildResponse(Long userId) {
        User user = userRepository.findWithRolesById(userId).orElseThrow(StaffManagementService::staffNotFound);
        StaffProfile staffProfile = loadStaffProfile(userId);
        return StaffResponse.from(user, staffProfile, branchAccessService.getAssignedBranches(userId));
    }

    private static void trackChange(
            Map<String, Object> before, Map<String, Object> after, String field, String oldValue, String newValue) {
        if (!oldValue.equals(newValue)) {
            before.put(field, oldValue);
            after.put(field, newValue);
        }
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }

    private static AppException employeeCodeAlreadyUsed() {
        return new AppException("Mã nhân viên đã được sử dụng", HttpStatus.CONFLICT, "EMPLOYEE_CODE_ALREADY_EXISTS");
    }

    private static AppException accessDenied() {
        return new AppException("Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    private static AppException staffNotFound() {
        return new AppException("Không tìm thấy nhân viên", HttpStatus.NOT_FOUND, "STAFF_NOT_FOUND");
    }

    private record BranchSelection(Set<Long> cinemaIds, Long primaryCinemaId) {
    }
}
