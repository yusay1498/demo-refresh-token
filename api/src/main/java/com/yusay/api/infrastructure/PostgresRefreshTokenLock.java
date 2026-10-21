package com.yusay.api.infrastructure;

import com.yusay.api.application.port.RefreshTokenLock;
import com.yusay.api.infrastructure.dao.RefreshTokenLockDao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PostgresRefreshTokenLock implements RefreshTokenLock {
    private final RefreshTokenLockDao lockDao;

    public PostgresRefreshTokenLock(RefreshTokenLockDao lockDao) {
        this.lockDao = lockDao;
    }

    @Override
    public void lockFamily(String familyId) {
        requireTransaction();
        lockDao.lockFamily(familyId);
    }

    @Override
    public void lockUsername(String username) {
        requireTransaction();
        lockDao.lockUsername(username);
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Refresh token locks require an active transaction");
        }
    }
}