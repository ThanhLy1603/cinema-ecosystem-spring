package com.cinema.modules.audit.service;

import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import com.cinema.modules.auth.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceChangeAuditorTest {

    private static final Long ACTOR_USER_ID = 8L;

    @Mock
    private IAuditLogService auditLogService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private PriceChangeAuditor priceChangeAuditor;

    @BeforeEach
    void setUp() {
        priceChangeAuditor = new PriceChangeAuditor(auditLogService, currentUserProvider);
    }

    @Test
    void recordPriceChangeWritesOldAndNewPriceWithCurrentActor() {
        when(currentUserProvider.findCurrentUserId()).thenReturn(ACTOR_USER_ID);

        priceChangeAuditor.recordPriceChange(AuditAction.FNB_ITEM_PRICE_CHANGED, null, "FNB_ITEM", 5L, "Nước suối",
                new BigDecimal("20000"), new BigDecimal("30000"));

        verify(auditLogService).record(
                ACTOR_USER_ID, null, AuditAction.FNB_ITEM_PRICE_CHANGED, "FNB_ITEM", "5",
                "Nước suối: 20.000đ → 30.000đ");
    }

    @Test
    void recordPriceChangeIgnoresUnchangedPrice() {
        priceChangeAuditor.recordPriceChange(AuditAction.FNB_ITEM_PRICE_CHANGED, null, "FNB_ITEM", 5L, "Nước suối",
                new BigDecimal("20000.00"), new BigDecimal("20000"));

        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void recordInitialPriceWritesFirstPrice() {
        when(currentUserProvider.findCurrentUserId()).thenReturn(ACTOR_USER_ID);

        priceChangeAuditor.recordInitialPrice(
                AuditAction.FNB_COMBO_PRICE_CHANGED, "FNB_COMBO", 2L, "Combo đôi", new BigDecimal("109000"));

        verify(auditLogService).record(
                ACTOR_USER_ID, null, AuditAction.FNB_COMBO_PRICE_CHANGED, "FNB_COMBO", "2",
                "Combo đôi: giá ban đầu 109.000đ");
    }
}
