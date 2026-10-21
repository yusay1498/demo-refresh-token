package com.yusay.api.domain.entity.vo;

import java.util.Objects;

public record RefreshTokenHash(String hexValue) {
    public RefreshTokenHash {
        Objects.requireNonNull(hexValue, "hexValue must not be null");

        if (!hexValue.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 hash must be 64 lowercase hexadecimal characters");
        }
    }

    @Override
    public String toString() {
        return "RefreshTokenHash[redacted]";
    }
}
