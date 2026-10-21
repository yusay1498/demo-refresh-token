package com.yusay.api.infrastructure.dao;

import com.yusay.api.domain.entity.RefreshToken;
import com.yusay.api.domain.entity.RefreshTokenFamily;
import com.yusay.api.domain.entity.vo.RefreshTokenHash;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RefreshTokenFamilyDao {
    private final JdbcClient jdbcClient;

    public RefreshTokenFamilyDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<RefreshTokenFamily> selectById(String id) {
        return jdbcClient.sql("""
                SELECT
                    family.id AS family_id,
                    family.username,
                    family.created_at,
                    family.absolute_expires_at,
                    family.revoked_at,
                    token.id AS token_id,
                    token.family_id AS token_family_id,
                    token.token_hash,
                    token.issued_at,
                    token.expires_at,
                    token.consumed_at,
                    token.revoked_at AS token_revoked_at,
                    token.replaced_by_id
                FROM refresh_token_family AS family
                INNER JOIN refresh_token AS token
                    ON token.family_id = family.id
                    AND token.consumed_at IS NULL
                WHERE family.id = :id
                """)
            .param("id", id)
            .query(this::mapFamily)
            .optional();
    }

    public List<RefreshTokenFamily> selectByUsername(String username) {
        return jdbcClient.sql("""
                SELECT
                    family.id AS family_id,
                    family.username,
                    family.created_at,
                    family.absolute_expires_at,
                    family.revoked_at,
                    token.id AS token_id,
                    token.family_id AS token_family_id,
                    token.token_hash,
                    token.issued_at,
                    token.expires_at,
                    token.consumed_at,
                    token.revoked_at AS token_revoked_at,
                    token.replaced_by_id
                FROM refresh_token_family AS family
                INNER JOIN refresh_token AS token
                    ON token.family_id = family.id
                    AND token.consumed_at IS NULL
                WHERE family.username = :username
                ORDER BY family.id
                """)
            .param("username", username)
            .query(this::mapFamily)
            .list();
    }

    public void insert(RefreshTokenFamily family) {
        jdbcClient.sql("""
                INSERT INTO refresh_token_family (
                    id,
                    username,
                    created_at,
                    absolute_expires_at,
                    revoked_at
                ) VALUES (
                    :id,
                    :username,
                    :createdAt,
                    :absoluteExpiresAt,
                    :revokedAt
                )
                """)
            .param("id", family.id())
            .param("username", family.username())
            .param("createdAt", family.createdAt())
            .param("absoluteExpiresAt", family.absoluteExpiresAt())
            .param("revokedAt", family.revokedAt())
            .update();
    }

    public int update(RefreshTokenFamily family) {
        return jdbcClient.sql("""
                UPDATE refresh_token_family
                SET absolute_expires_at = :absoluteExpiresAt,
                    revoked_at = :revokedAt
                WHERE id = :id
                """)
            .param("id", family.id())
            .param("absoluteExpiresAt", family.absoluteExpiresAt())
            .param("revokedAt", family.revokedAt())
            .update();
    }

    private RefreshTokenFamily mapFamily(ResultSet resultSet, int rowNumber) throws SQLException {
        RefreshToken currentToken = new RefreshToken(
            resultSet.getString("token_id"),
            resultSet.getString("token_family_id"),
            new RefreshTokenHash(resultSet.getString("token_hash").trim()),
            resultSet.getObject("issued_at", OffsetDateTime.class),
            resultSet.getObject("expires_at", OffsetDateTime.class),
            resultSet.getObject("consumed_at", OffsetDateTime.class),
            resultSet.getObject("token_revoked_at", OffsetDateTime.class),
            resultSet.getString("replaced_by_id")
        );

        return new RefreshTokenFamily(
            resultSet.getString("family_id"),
            resultSet.getString("username"),
            resultSet.getObject("created_at", OffsetDateTime.class),
            resultSet.getObject("absolute_expires_at", OffsetDateTime.class),
            resultSet.getObject("revoked_at", OffsetDateTime.class),
            currentToken
        );
    }
}