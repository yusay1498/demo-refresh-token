package com.yusay.api.application.event;

public record AuthAuditEvent(
    String action,
    String outcome,
    String reason,
    String familyId,
    String scope,
    Timing timing
) {
    public enum Timing {
        IMMEDIATE,
        AFTER_COMMIT,
        AFTER_REUSE_REVOCATION
    }
}
