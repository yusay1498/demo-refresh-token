package com.yusay.api.aop;

import com.yusay.api.application.event.AuthAuditEvent;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAuditLoggerTest {
    private final AuthAuditLogger audit = new AuthAuditLogger();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AuthAuditLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        MDC.clear();
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void givenAuditEvent_whenReceived_thenRecordsFieldsAndRestoresMdc() {
        MDC.put("trace.id", "test-trace");
        audit.onAuditEvent(new AuthAuditEvent(
            "sign_in_failed", "rejected", "credentials_rejected", "", "none", AuthAuditEvent.Timing.IMMEDIATE
        ));
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getMDCPropertyMap())
            .containsEntry("event.action", "sign_in_failed")
            .containsEntry("event.outcome", "rejected")
            .doesNotContainKeys("password", "token", "refreshToken", "session.family_id");
        assertThat(MDC.getCopyOfContextMap()).containsOnlyKeys("trace.id");
    }

    @Test
    void givenReuse_whenCommitted_thenRecordsSuccessfulRevocation() {
        audit.onAuditEvent(new AuthAuditEvent(
            "reuse_revocation_completed", "", "token_reuse", "family-id", "family",
            AuthAuditEvent.Timing.AFTER_REUSE_REVOCATION
        ));
        assertThat(appender.list).isEmpty();
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getMDCPropertyMap())
            .containsEntry("event.action", "reuse_revocation_completed")
            .containsEntry("event.outcome", "success");
    }

    @Test
    void givenTransaction_whenCommitted_thenRecordsSuccessWithCapturedContext() {
        MDC.put("trace.id", "test-trace");
        audit.afterCommit("sign_in_succeeded", "authenticated", "family-id", "none");
        assertThat(appender.list).isEmpty();
        MDC.clear();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getMDCPropertyMap())
            .containsEntry("event.action", "sign_in_succeeded")
            .containsEntry("event.outcome", "success")
            .containsEntry("trace.id", "test-trace")
            .containsEntry("session.family_id", "family-id");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void givenTransaction_whenRolledBack_thenDoesNotRecordSuccess() {
        audit.afterCommit("session_revoked", "sign_out", "family-id", "family");
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(appender.list).isEmpty();
    }

    @Test
    void givenReuse_whenRevocationRollsBack_thenKeepsDetectionAndRecordsFailure() {
        MDC.put("trace.id", "test-trace");
        audit.record("refresh_token_reuse_detected", "detected", "consumed_token_presented", "family-id", "family");
        audit.afterReuseRevocation("family-id");
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.getFirst().getMDCPropertyMap())
            .containsEntry("event.action", "refresh_token_reuse_detected");
        assertThat(appender.list.getLast().getMDCPropertyMap())
            .containsEntry("event.action", "reuse_revocation_completed")
            .containsEntry("event.outcome", "failure");
        assertThat(MDC.getCopyOfContextMap()).containsOnlyKeys("trace.id");
    }
}
