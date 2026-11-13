package com.cinema.modules.audit.dto.response;

import com.cinema.modules.audit.enums.AuditAction;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        AuditAction action,
        String actorName,
        Long cinemaId,
        String cinemaName,
        String targetType,
        String targetCode,
        String detail,
        Instant createdAt) {
}
