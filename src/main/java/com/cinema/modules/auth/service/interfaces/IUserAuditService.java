package com.cinema.modules.auth.service.interfaces;

import com.cinema.modules.auth.enums.AuditAction;

import java.util.Map;

public interface IUserAuditService {

    void record(Long actorId, Long targetUserId, AuditAction auditAction, Map<String, Object> details, String ipAddress);
}
