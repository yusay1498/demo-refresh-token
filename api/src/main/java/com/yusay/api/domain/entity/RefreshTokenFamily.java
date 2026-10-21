package com.yusay.api.domain.entity;

import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Objects;

public record RefreshTokenFamily(
        String id,
        String username,
        OffsetDateTime createdAt,
        OffsetDateTime absoluteExpiresAt,
        @Nullable
        OffsetDateTime revokedAt,
        RefreshToken currentToken
) {
    public RefreshTokenFamily {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt must not be null");
        Objects.requireNonNull(currentToken, "currentToken must not be null");

        if (id.isBlank() || username.isBlank()) {
            throw new IllegalArgumentException("id and username must not be blank");
        }
        if (!absoluteExpiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("absoluteExpiresAt must be after createdAt");
        }
        if (revokedAt != null && revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }
        if (!id.equals(currentToken.familyId())) {
            throw new IllegalArgumentException("currentToken must belong to this family");
        }
        if (currentToken.issuedAt().isBefore(createdAt)
                || currentToken.expiresAt().isAfter(absoluteExpiresAt)) {
            throw new IllegalArgumentException("currentToken timestamps must be within the family lifetime");
        }
        if (revokedAt == null && (currentToken.isConsumed() || currentToken.isRevoked())) {
            throw new IllegalArgumentException("an active family must have an active current token");
        }
    }

    public boolean isActiveAt(OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        return revokedAt == null && now.isBefore(absoluteExpiresAt);
    }

    public RefreshTokenFamily revoke(OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        if (revokedAt != null) {
            return this;
        }
        if (now.isBefore(createdAt)) {
            throw new IllegalArgumentException("now must not be before createdAt");
        }
        return new RefreshTokenFamily(
                id, username, createdAt, absoluteExpiresAt, now, currentToken.revoke(now)
        );
    }

    public RotationResult rotate(
            RefreshToken presentedToken,
            RefreshToken replacementToken,
            OffsetDateTime now
    ) {
        Objects.requireNonNull(presentedToken, "presentedToken must not be null");
        Objects.requireNonNull(replacementToken, "replacementToken must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (!isActiveAt(now)) {
            return new RotationRejected(RotationFailure.FAMILY_INACTIVE);
        }
        if (!id.equals(presentedToken.familyId()) || !id.equals(replacementToken.familyId())) {
            return new RotationRejected(RotationFailure.FAMILY_MISMATCH);
        }
        if (presentedToken.isConsumed()) {
            return new TokenReuseDetected(revoke(now));
        }
        if (presentedToken.isRevoked()) {
            return new RotationRejected(RotationFailure.TOKEN_REVOKED);
        }
        if (!presentedToken.isActiveAt(now)) {
            return new RotationRejected(RotationFailure.TOKEN_EXPIRED);
        }
        if (!currentToken.id().equals(presentedToken.id())
                || !currentToken.tokenHash().equals(presentedToken.tokenHash())) {
            return new RotationRejected(RotationFailure.TOKEN_NOT_CURRENT);
        }
        if (!replacementToken.isActiveAt(now)
                || !replacementToken.issuedAt().isEqual(now)
                || replacementToken.issuedAt().isBefore(createdAt)
                || replacementToken.expiresAt().isAfter(absoluteExpiresAt)
                || replacementToken.id().equals(presentedToken.id())
                || replacementToken.tokenHash().equals(presentedToken.tokenHash())) {
            return new RotationRejected(RotationFailure.INVALID_REPLACEMENT);
        }

        RefreshToken consumedToken = currentToken.consume(replacementToken.id(), now);
        RefreshTokenFamily rotatedFamily = new RefreshTokenFamily(
                id, username, createdAt, absoluteExpiresAt, null, replacementToken
        );
        return new RotationAccepted(rotatedFamily, consumedToken, replacementToken);
    }

    public sealed interface RotationResult
            permits RotationAccepted, TokenReuseDetected, RotationRejected {
    }

    public record RotationAccepted(
            RefreshTokenFamily family,
            RefreshToken consumedToken,
            RefreshToken replacementToken
    ) implements RotationResult {
    }

    public record TokenReuseDetected(
            RefreshTokenFamily revokedFamily
    ) implements RotationResult {
    }

    public record RotationRejected(RotationFailure failure) implements RotationResult {
    }

    public enum RotationFailure {
        FAMILY_INACTIVE,
        FAMILY_MISMATCH,
        TOKEN_REVOKED,
        TOKEN_EXPIRED,
        TOKEN_NOT_CURRENT,
        INVALID_REPLACEMENT
    }
}
