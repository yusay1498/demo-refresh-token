package com.yusay.api.domain.repository;

import com.yusay.api.domain.entity.RefreshTokenFamily;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenFamilyRepository {
    Optional<RefreshTokenFamily> findByIdForUpdate(String id);

    List<RefreshTokenFamily> findAllByUsernameForUpdate(String username);

    RefreshTokenFamily save(RefreshTokenFamily family);
}