package com.yusay.api.application.port;

public interface RefreshTokenLock {
    void lockFamily(String familyId);

    void lockUsername(String username);
}