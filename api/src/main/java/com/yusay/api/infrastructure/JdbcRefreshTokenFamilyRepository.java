package com.yusay.api.infrastructure;

import com.yusay.api.domain.entity.RefreshTokenFamily;
import com.yusay.api.domain.repository.RefreshTokenFamilyRepository;
import com.yusay.api.infrastructure.dao.RefreshTokenFamilyDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcRefreshTokenFamilyRepository implements RefreshTokenFamilyRepository {
    private final RefreshTokenFamilyDao familyDao;

    public JdbcRefreshTokenFamilyRepository(RefreshTokenFamilyDao familyDao) {
        this.familyDao = familyDao;
    }

    @Override
    public Optional<RefreshTokenFamily> findById(String id) {
        return familyDao.selectById(id);
    }

    @Override
    public List<RefreshTokenFamily> findAllByUsername(String username) {
        return familyDao.selectByUsername(username);
    }

    @Override
    public RefreshTokenFamily save(RefreshTokenFamily family) {
        if (familyDao.update(family) == 0) {
            familyDao.insert(family);
        }

        return family;
    }
}