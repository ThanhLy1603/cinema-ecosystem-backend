package com.cinema.modules.audit.service;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.audit.dto.response.AuditLogResponse;
import com.cinema.modules.audit.entity.AuditLog;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.repository.AuditLogRepository;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.scope.CinemaScope;
import com.cinema.modules.auth.scope.CinemaScopeResolver;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AuditLogService implements IAuditLogService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DETAIL_LENGTH = 1000;

    private final AuditLogRepository auditLogRepository;
    private final CinemaScopeResolver cinemaScopeResolver;
    private final IUserQueryService userQueryService;
    private final ICinemaQueryService cinemaQueryService;
    private final Clock clock;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            CinemaScopeResolver cinemaScopeResolver,
            IUserQueryService userQueryService,
            ICinemaQueryService cinemaQueryService,
            Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.cinemaScopeResolver = cinemaScopeResolver;
        this.userQueryService = userQueryService;
        this.cinemaQueryService = cinemaQueryService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void record(
            Long actorUserId,
            Long cinemaId,
            AuditAction auditAction,
            String targetType,
            String targetCode,
            String detail) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUserId(actorUserId);
        auditLog.setCinemaId(cinemaId);
        auditLog.setAction(auditAction);
        auditLog.setTargetType(targetType);
        auditLog.setTargetCode(targetCode);
        auditLog.setDetail(detail != null && detail.length() > MAX_DETAIL_LENGTH
                ? detail.substring(0, MAX_DETAIL_LENGTH)
                : detail);
        auditLog.setCreatedAt(Instant.now(clock));
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<AuditLogResponse> findLogs(
            Long managerUserId,
            Long cinemaId,
            Long actorUserId,
            AuditAction auditAction,
            Instant from,
            Instant to,
            int page,
            int size) {
        CinemaScope cinemaScope = cinemaScopeResolver.resolve(managerUserId, cinemaId);
        if (cinemaScope.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }
        Page<AuditLog> auditLogs = auditLogRepository.findAll(
                buildSpecification(cinemaScope, actorUserId, auditAction, from, to),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, String> actorNames = new HashMap<>();
        auditLogs.getContent().stream().map(AuditLog::getActorUserId).filter(Objects::nonNull).distinct()
                .forEach(loggedActorUserId -> actorNames.put(
                        loggedActorUserId,
                        userQueryService.findContact(loggedActorUserId)
                                .map(UserContactResponse::fullName)
                                .orElse("Người dùng #" + loggedActorUserId)));
        Map<Long, String> cinemaNames = cinemaQueryService.findCinemaNames(
                auditLogs.getContent().stream().map(AuditLog::getCinemaId).filter(Objects::nonNull).distinct().toList());
        List<AuditLogResponse> auditLogResponses = auditLogs.getContent().stream()
                .map(auditLog -> new AuditLogResponse(
                        auditLog.getId(),
                        auditLog.getAction(),
                        auditLog.getActorUserId() == null ? null : actorNames.get(auditLog.getActorUserId()),
                        auditLog.getCinemaId(),
                        auditLog.getCinemaId() == null ? null : cinemaNames.get(auditLog.getCinemaId()),
                        auditLog.getTargetType(),
                        auditLog.getTargetCode(),
                        auditLog.getDetail(),
                        auditLog.getCreatedAt()))
                .toList();
        return PageResponse.of(auditLogs, auditLogResponses);
    }

    private Specification<AuditLog> buildSpecification(
            CinemaScope cinemaScope, Long actorUserId, AuditAction auditAction, Instant from, Instant to) {
        return (auditLog, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!cinemaScope.allCinemas()) {
                predicates.add(auditLog.get("cinemaId").in(cinemaScope.cinemaIds()));
            }
            if (actorUserId != null) {
                predicates.add(criteriaBuilder.equal(auditLog.get("actorUserId"), actorUserId));
            }
            if (auditAction != null) {
                predicates.add(criteriaBuilder.equal(auditLog.get("action"), auditAction));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(auditLog.<Instant>get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(auditLog.<Instant>get("createdAt"), to));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
