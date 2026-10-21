package com.yusay.api.domain.entity;

import com.yusay.api.domain.entity.vo.RefreshTokenHash;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Objects;

public record RefreshToken(
        String id,
        String familyId,
        RefreshTokenHash tokenHash,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        @Nullable
        OffsetDateTime consumedAt,
        @Nullable
        OffsetDateTime revokedAt,
        @Nullable
        String replacedById
) {
    public RefreshToken {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        if (id.isBlank() || familyId.isBlank()) {
            throw new IllegalArgumentException("id and familyId must not be blank");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        if (consumedAt != null && revokedAt != null) {
            throw new IllegalArgumentException("a token cannot be consumed and revoked");
        }
        if ((consumedAt == null) != (replacedById == null)) {
            throw new IllegalArgumentException("replacedById must be set only for a consumed token");
        }
        if (consumedAt != null && consumedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("consumedAt must not be before issuedAt");
        }
        if (revokedAt != null && revokedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("revokedAt must not be before issuedAt");
        }
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isActiveAt(OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        return !isConsumed() && !isRevoked() && now.isBefore(expiresAt);
    }

    public RefreshToken consume(String replacementId, OffsetDateTime now) {
        Objects.requireNonNull(replacementId, "replacementId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!isActiveAt(now)) {
            throw new IllegalStateException("Only an active token can be consumed");
        }
        if (replacementId.isBlank() || id.equals(replacementId)) {
            throw new IllegalArgumentException("replacementId must identify a different token");
        }
        if (now.isBefore(issuedAt)) {
            throw new IllegalArgumentException("now must not be before issuedAt");
        }

        return new RefreshToken(
                id, familyId, tokenHash, issuedAt, expiresAt, now, null, replacementId
        );
    }

    public RefreshToken revoke(OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        if (isConsumed() || isRevoked()) {
            return this;
        }
        if (now.isBefore(issuedAt)) {
            throw new IllegalArgumentException("now must not be before issuedAt");
        }

        return new RefreshToken(
                id, familyId, tokenHash, issuedAt, expiresAt, null, now, null
        );
    }
}
