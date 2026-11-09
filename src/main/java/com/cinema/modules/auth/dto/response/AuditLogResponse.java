package com.cinema.modules.auth.dto.response;

import com.cinema.modules.auth.entity.UserAuditLog;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorName,
        Long targetUserId,
        String action,
        Map<String, Object> details,
        String ipAddress,
        Instant createdAt) {

    public static AuditLogResponse from(UserAuditLog userAuditLog, String actorName) {
        return new AuditLogResponse(
                userAuditLog.getId(),
                userAuditLog.getActorId(),
                actorName,
                userAuditLog.getTargetUserId(),
                userAuditLog.getAction().name(),
                userAuditLog.getDetails(),
                userAuditLog.getIpAddress(),
                userAuditLog.getCreatedAt());
    }
}
