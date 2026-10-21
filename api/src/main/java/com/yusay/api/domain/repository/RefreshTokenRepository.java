package com.yusay.api.domain.repository;

import com.yusay.api.domain.entity.RefreshToken;
import com.yusay.api.domain.entity.vo.RefreshTokenHash;

import java.util.Optional;

public interface RefreshTokenRepository {
    Optional<RefreshToken> findByHash(RefreshTokenHash tokenHash);

    Optional<RefreshToken> findById(String id);

    RefreshToken save(RefreshToken refreshToken);
}
