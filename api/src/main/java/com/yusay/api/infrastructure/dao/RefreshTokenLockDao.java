package com.yusay.api.infrastructure.dao;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenLockDao {
    private final JdbcClient jdbcClient;

    public RefreshTokenLockDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void lockFamily(String familyId) {
        jdbcClient.sql("""
                SELECT id
                FROM refresh_token_family
                WHERE id = :familyId
                FOR UPDATE
                """)
            .param("familyId", familyId)
            .query(String.class)
            .optional();
    }

    public void lockUsername(String username) {
        jdbcClient.sql("""
                SELECT pg_advisory_xact_lock(hashtextextended(:username, 0))
                """)
            .param("username", username)
            .query(resultSet -> {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Username lock query returned no row");
                }
                return null;
            });
    }
}
