package com.cinema.modules.audit.service.interfaces;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.audit.dto.response.AuditLogResponse;
import com.cinema.modules.audit.enums.AuditAction;

import java.time.Instant;

public interface IAuditLogService {

    void record(
            Long actorUserId,
            Long cinemaId,
            AuditAction auditAction,
            String targetType,
            String targetCode,
            String detail);

    PageResponse<AuditLogResponse> findLogs(
            Long managerUserId,
            Long cinemaId,
            Long actorUserId,
            AuditAction auditAction,
            Instant from,
            Instant to,
            int page,
            int size);
}
