package com.cinema.modules.auth.service;

import com.cinema.modules.auth.entity.UserAuditLog;
import com.cinema.modules.auth.enums.AuditAction;
import com.cinema.modules.auth.repository.UserAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class UserAuditService {

    private final UserAuditLogRepository userAuditLogRepository;

    public UserAuditService(UserAuditLogRepository userAuditLogRepository) {
        this.userAuditLogRepository = userAuditLogRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            Long actorId, Long targetUserId, AuditAction auditAction, Map<String, Object> details, String ipAddress) {
        UserAuditLog userAuditLog = new UserAuditLog();
        userAuditLog.setActorId(actorId);
        userAuditLog.setTargetUserId(targetUserId);
        userAuditLog.setAction(auditAction);
        userAuditLog.setDetails(details);
        userAuditLog.setIpAddress(ipAddress);
        userAuditLogRepository.save(userAuditLog);
    }
}
