package com.yusay.api.infrastructure;

import com.yusay.api.domain.entity.RefreshToken;
import com.yusay.api.domain.entity.vo.RefreshTokenHash;
import com.yusay.api.domain.repository.RefreshTokenRepository;
import com.yusay.api.infrastructure.dao.RefreshTokenDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {
    private final RefreshTokenDao tokenDao;

    public JdbcRefreshTokenRepository(RefreshTokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    @Override
    public Optional<RefreshToken> findByHash(RefreshTokenHash tokenHash) {
        return tokenDao.selectByHash(tokenHash);
    }

    @Override
    public Optional<RefreshToken> findById(String id) {
        return tokenDao.selectById(id);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        if (tokenDao.update(refreshToken) == 0) {
            tokenDao.insert(refreshToken);
        }
        return refreshToken;
    }
}