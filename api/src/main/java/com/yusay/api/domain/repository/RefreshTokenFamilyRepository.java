package com.yusay.api.domain.repository;

import com.yusay.api.domain.entity.RefreshTokenFamily;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenFamilyRepository {
    Optional<RefreshTokenFamily> findById(String id);

    List<RefreshTokenFamily> findAllByUsername(String username);

    List<RefreshTokenFamily> findAllActiveByUsername(String username, OffsetDateTime now);

    RefreshTokenFamily save(RefreshTokenFamily family);
}
