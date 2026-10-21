package com.yusay.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.auth.refresh-token")
public record RefreshTokenProperties(
    @DefaultValue("8h") Duration idleTimeout,
    @DefaultValue("24h") Duration absoluteTimeout,
    @DefaultValue("32") int tokenLengthBytes
) {
    public RefreshTokenProperties {
        Objects.requireNonNull(idleTimeout, "idleTimeout must not be null");
        Objects.requireNonNull(absoluteTimeout, "absoluteTimeout must not be null");

        if (idleTimeout.isZero() || idleTimeout.isNegative()) {
            throw new IllegalArgumentException("idleTimeout must be positive");
        }
        if (absoluteTimeout.isZero() || absoluteTimeout.isNegative()) {
            throw new IllegalArgumentException("absoluteTimeout must be positive");
        }
        if (absoluteTimeout.compareTo(idleTimeout) < 0) {
            throw new IllegalArgumentException("absoluteTimeout must not be shorter than idleTimeout");
        }
        if (tokenLengthBytes < 32) {
            throw new IllegalArgumentException("tokenLengthBytes must be at least 32");
        }
    }
}
