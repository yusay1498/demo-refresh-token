package com.yusay.api.infrastructure.dao;

import com.yusay.api.domain.entity.RefreshToken;
import com.yusay.api.domain.entity.vo.RefreshTokenHash;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RefreshTokenDao {
    private final JdbcClient jdbcClient;

    public RefreshTokenDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<RefreshToken> selectByHash(RefreshTokenHash tokenHash) {
        return jdbcClient.sql("""
                SELECT
                    id,
                    family_id,
                    token_hash,
                    issued_at,
                    expires_at,
                    consumed_at,
                    revoked_at,
                    replaced_by_id
                FROM refresh_token
                WHERE token_hash = :tokenHash
                """)
            .param("tokenHash", tokenHash.hexValue())
            .query(this::mapRow)
            .optional();
    }

    public Optional<RefreshToken> selectById(String id) {
        return jdbcClient.sql("""
                SELECT
                    id,
                    family_id,
                    token_hash,
                    issued_at,
                    expires_at,
                    consumed_at,
                    revoked_at,
                    replaced_by_id
                FROM refresh_token
                WHERE id = :id
                """)
            .param("id", id)
            .query(this::mapRow)
            .optional();
    }

    public List<RefreshToken> selectCurrentByFamilyIds(List<String> familyIds) {
        if (familyIds.isEmpty()) {
            return List.of();
        }

                return jdbcClient.sql("""
                                SELECT
                                        id,
                                        family_id,
                                        token_hash,
                                        issued_at,
                                        expires_at,
                                        consumed_at,
                                        revoked_at,
                                        replaced_by_id
                                FROM refresh_token
                                WHERE family_id IN (:familyIds)
                                    AND consumed_at IS NULL
                                ORDER BY family_id
                """)
            .param("familyIds", familyIds)
            .query(this::mapRow)
            .list();
    }

    public void insert(RefreshToken token) {
        jdbcClient.sql("""
                INSERT INTO refresh_token (
                    id,
                    family_id,
                    token_hash,
                    issued_at,
                    expires_at,
                    consumed_at,
                    revoked_at,
                    replaced_by_id
                ) VALUES (
                    :id,
                    :familyId,
                    :tokenHash,
                    :issuedAt,
                    :expiresAt,
                    :consumedAt,
                    :revokedAt,
                    :replacedById
                )
                """)
            .param("id", token.id())
            .param("familyId", token.familyId())
            .param("tokenHash", token.tokenHash().hexValue())
            .param("issuedAt", token.issuedAt())
            .param("expiresAt", token.expiresAt())
            .param("consumedAt", token.consumedAt())
            .param("revokedAt", token.revokedAt())
            .param("replacedById", token.replacedById())
            .update();
    }

    public int update(RefreshToken token) {
        return jdbcClient.sql("""
                UPDATE refresh_token
                SET family_id = :familyId,
                    token_hash = :tokenHash,
                    issued_at = :issuedAt,
                    expires_at = :expiresAt,
                    consumed_at = :consumedAt,
                    revoked_at = :revokedAt,
                    replaced_by_id = :replacedById
                WHERE id = :id
                """)
            .param("id", token.id())
            .param("familyId", token.familyId())
            .param("tokenHash", token.tokenHash().hexValue())
            .param("issuedAt", token.issuedAt())
            .param("expiresAt", token.expiresAt())
            .param("consumedAt", token.consumedAt())
            .param("revokedAt", token.revokedAt())
            .param("replacedById", token.replacedById())
            .update();
    }

    private RefreshToken mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RefreshToken(
            resultSet.getString("id"),
            resultSet.getString("family_id"),
            new RefreshTokenHash(resultSet.getString("token_hash").trim()),
            resultSet.getObject("issued_at", OffsetDateTime.class),
            resultSet.getObject("expires_at", OffsetDateTime.class),
            resultSet.getObject("consumed_at", OffsetDateTime.class),
            resultSet.getObject("revoked_at", OffsetDateTime.class),
            resultSet.getString("replaced_by_id")
        );
    }
}
