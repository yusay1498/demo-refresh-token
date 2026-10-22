package com.yusay.api.aop;

import com.yusay.api.application.event.AuthAuditEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuthAuditLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthAuditLogger.class);

    @EventListener
    public void onAuditEvent(AuthAuditEvent event) {
        switch (event.timing()) {
            case IMMEDIATE -> record(event.action(), event.outcome(), event.reason(), event.familyId(), event.scope());
            case AFTER_COMMIT -> afterCommit(event.action(), event.reason(), event.familyId(), event.scope());
            case AFTER_REUSE_REVOCATION -> afterReuseRevocation(event.familyId());
        }
    }

    public void record(String action, String outcome, String reason, String familyId, String scope) {
        write(action, outcome, reason, familyId, scope, MDC.getCopyOfContextMap());
    }

    public void afterCommit(String action, String reason, String familyId, String scope) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                write(action, "success", reason, familyId, scope, context);
            }
        });
    }

    public void afterReuseRevocation(String familyId) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                String outcome = switch (status) {
                    case STATUS_COMMITTED -> "success";
                    case STATUS_ROLLED_BACK -> "failure";
                    default -> "unknown";
                };
                write("reuse_revocation_completed", outcome, "token_reuse", familyId, "family", context);
            }
        });
    }

    private void write(String action, String outcome, String reason, String familyId, String scope,
                       Map<String, String> context) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.setContextMap(context == null ? new HashMap<>() : context);
            MDC.put("event.category", "authentication");
            MDC.put("event.action", action);
            MDC.put("event.outcome", outcome);
            MDC.put("reason", reason);
            MDC.put("revocation.scope", scope);
            if (!familyId.isEmpty()) MDC.put("session.family_id", familyId);
            else MDC.remove("session.family_id");
            if (action.equals("refresh_token_reuse_detected") || !outcome.equals("success") && !outcome.equals("rejected")) {
                LOGGER.warn("Authentication audit event");
            } else {
                LOGGER.info("Authentication audit event");
            }
        } finally {
            if (previous == null) MDC.clear();
            else MDC.setContextMap(previous);
        }
    }
}
