package com.cinema.modules.audit.service;

import com.cinema.common.util.VndFormatter;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import com.cinema.modules.auth.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class PriceChangeAuditor {

    private final IAuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;

    public void recordInitialPrice(
            AuditAction auditAction, String targetType, Long targetId, String label, BigDecimal price) {
        recordDetail(auditAction, null, targetType, targetId, label + ": giá ban đầu " + VndFormatter.format(price));
    }

    public void recordPriceChange(
            AuditAction auditAction,
            Long cinemaId,
            String targetType,
            Long targetId,
            String label,
            BigDecimal oldPrice,
            BigDecimal newPrice) {
        if (oldPrice.compareTo(newPrice) == 0) {
            return;
        }
        recordDetail(auditAction, cinemaId, targetType, targetId,
                label + ": " + VndFormatter.format(oldPrice) + " → " + VndFormatter.format(newPrice));
    }

    public void recordDetail(
            AuditAction auditAction, Long cinemaId, String targetType, Long targetId, String detail) {
        auditLogService.record(
                currentUserProvider.findCurrentUserId(), cinemaId, auditAction, targetType, String.valueOf(targetId),
                detail);
    }
}
