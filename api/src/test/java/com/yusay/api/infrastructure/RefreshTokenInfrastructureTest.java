package com.yusay.api.infrastructure;

import com.yusay.api.TestcontainersConfiguration;
import com.yusay.api.domain.entity.RefreshToken;
import com.yusay.api.domain.entity.RefreshTokenFamily;
import com.yusay.api.domain.entity.vo.RefreshTokenHash;
import com.yusay.api.domain.repository.RefreshTokenFamilyRepository;
import com.yusay.api.domain.repository.RefreshTokenRepository;
import com.yusay.api.infrastructure.dao.RefreshTokenDao;
import com.yusay.api.infrastructure.dao.RefreshTokenFamilyDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    TestcontainersConfiguration.class,
    JdbcRefreshTokenFamilyRepository.class,
    JdbcRefreshTokenRepository.class,
    RefreshTokenFamilyDao.class,
    RefreshTokenDao.class
})
class RefreshTokenInfrastructureTest {
    private static final String FAMILY_ID_1 = "00000000-0000-0000-0000-000000000001";
    private static final String FAMILY_ID_2 = "00000000-0000-0000-0000-000000000002";
    private static final String TOKEN_ID_1 = "10000000-0000-0000-0000-000000000001";
    private static final String TOKEN_ID_2 = "10000000-0000-0000-0000-000000000002";
    private static final String TOKEN_ID_3 = "10000000-0000-0000-0000-000000000003";
    private static final String HASH_1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_2 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String HASH_3 = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-21T00:00:00Z");
    private static final OffsetDateTime TOKEN_ISSUED_AT = OffsetDateTime.parse("2026-10-21T00:10:00Z");
    private static final OffsetDateTime TOKEN_EXPIRES_AT = OffsetDateTime.parse("2026-10-21T08:10:00Z");
    private static final OffsetDateTime FAMILY_EXPIRES_AT = OffsetDateTime.parse("2026-10-22T00:00:00Z");
    private static final OffsetDateTime REVOKED_AT = OffsetDateTime.parse("2026-10-21T01:00:00Z");

    @Autowired
    RefreshTokenFamilyRepository familyRepository;

    @Autowired
    RefreshTokenRepository tokenRepository;

    @Autowired
    JdbcClient jdbcClient;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @Test
    @Sql(statements = {
        """
            INSERT INTO refresh_token_family (id, username, created_at, absolute_expires_at)
            VALUES ('00000000-0000-0000-0000-000000000001', 'cid', '2026-10-21T00:00:00Z', '2026-10-22T00:00:00Z')
            ;
            """,
        """
            INSERT INTO refresh_token (id, family_id, token_hash, issued_at, expires_at)
            VALUES ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', '2026-10-21T00:10:00Z', '2026-10-21T08:10:00Z')
            ;
            """,
        """
            INSERT INTO refresh_token (id, family_id, token_hash, issued_at, expires_at, consumed_at, replaced_by_id)
            VALUES ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', '2026-10-21T00:00:00Z', '2026-10-21T08:00:00Z', '2026-10-21T00:10:00Z', '10000000-0000-0000-0000-000000000002')
            ;
            """
    })
    void givenConsumedToken_whenFindByHash_thenMapsAllTokenColumns() {
        RefreshToken expected = new RefreshToken(
            TOKEN_ID_1,
            FAMILY_ID_1,
            new RefreshTokenHash(HASH_1),
            CREATED_AT,
            OffsetDateTime.parse("2026-10-21T08:00:00Z"),
            TOKEN_ISSUED_AT,
            null,
            TOKEN_ID_2
        );

        assertThat(tokenRepository.findByHash(new RefreshTokenHash(HASH_1)))
            .contains(expected);
    }

    @Test
    @Sql(statements = {
        """
            INSERT INTO refresh_token_family (id, username, created_at, absolute_expires_at)
            VALUES ('00000000-0000-0000-0000-000000000001', 'cid', '2026-10-21T00:00:00Z', '2026-10-22T00:00:00Z')
            ;
            """,
        """
            INSERT INTO refresh_token_family (id, username, created_at, absolute_expires_at, revoked_at)
            VALUES ('00000000-0000-0000-0000-000000000002', 'cid', '2026-10-21T00:00:00Z', '2026-10-22T00:00:00Z', '2026-10-21T01:00:00Z')
            ;
            """,
        """
            INSERT INTO refresh_token (id, family_id, token_hash, issued_at, expires_at)
            VALUES ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', '2026-10-21T00:10:00Z', '2026-10-21T08:10:00Z')
            ;
            """,
        """
            INSERT INTO refresh_token (id, family_id, token_hash, issued_at, expires_at, revoked_at)
            VALUES ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', '2026-10-21T00:10:00Z', '2026-10-21T08:10:00Z', '2026-10-21T01:00:00Z')
            ;
            """,
        """
            INSERT INTO refresh_token (id, family_id, token_hash, issued_at, expires_at, consumed_at, replaced_by_id)
            VALUES ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', '2026-10-21T00:00:00Z', '2026-10-21T08:00:00Z', '2026-10-21T00:10:00Z', '10000000-0000-0000-0000-000000000002')
            ;
            """
    })
    void givenMultipleFamiliesAndTokenHistory_whenFindAllByUsername_thenReturnsEachCurrentToken() {
        RefreshToken expectedCurrentToken1 = new RefreshToken(
            TOKEN_ID_2,
            FAMILY_ID_1,
            new RefreshTokenHash(HASH_2),
            TOKEN_ISSUED_AT,
            TOKEN_EXPIRES_AT,
            null,
            null,
            null
        );
        RefreshToken expectedCurrentToken2 = new RefreshToken(
            TOKEN_ID_3,
            FAMILY_ID_2,
            new RefreshTokenHash(HASH_3),
            TOKEN_ISSUED_AT,
            TOKEN_EXPIRES_AT,
            null,
            REVOKED_AT,
            null
        );

        List<RefreshTokenFamily> result = familyRepository.findAllByUsername("cid");

        assertThat(result).containsExactly(
            new RefreshTokenFamily(FAMILY_ID_1, "cid", CREATED_AT, FAMILY_EXPIRES_AT, null, expectedCurrentToken1),
            new RefreshTokenFamily(FAMILY_ID_2, "cid", CREATED_AT, FAMILY_EXPIRES_AT, REVOKED_AT, expectedCurrentToken2)
        );
    }

    @Test
    @Sql(statements = {
        """
            INSERT INTO refresh_token_family (id, username, created_at, absolute_expires_at)
            VALUES ('00000000-0000-0000-0000-000000000001', 'cid', '2026-10-21T00:00:00Z', '2026-10-22T00:00:00Z');
            """,
        """
            INSERT INTO refresh_token (id, family_id, token_hash, issued_at, expires_at)
            VALUES ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', '2026-10-21T00:10:00Z', '2026-10-21T08:10:00Z');
            """
    })
    void givenCurrentToken_whenRotated_thenDeferredReplacementForeignKeyIsSatisfied() {
        RefreshToken consumedToken = new RefreshToken(
            TOKEN_ID_1,
            FAMILY_ID_1,
            new RefreshTokenHash(HASH_1),
            TOKEN_ISSUED_AT,
            TOKEN_EXPIRES_AT,
            REVOKED_AT,
            null,
            TOKEN_ID_2
        );
        RefreshToken replacementToken = new RefreshToken(
            TOKEN_ID_2,
            FAMILY_ID_1,
            new RefreshTokenHash(HASH_2),
            REVOKED_AT,
            FAMILY_EXPIRES_AT,
            null,
            null,
            null
        );

        tokenRepository.save(consumedToken);
        tokenRepository.save(replacementToken);
        jdbcClient.sql("SET CONSTRAINTS fk_refresh_token_replacement IMMEDIATE").update();

        assertThat(tokenRepository.findById(TOKEN_ID_1)).contains(consumedToken);
        assertThat(tokenRepository.findById(TOKEN_ID_2)).contains(replacementToken);
        assertThat(familyRepository.findById(FAMILY_ID_1))
            .get()
            .extracting(RefreshTokenFamily::currentToken)
            .isEqualTo(replacementToken);
    }

    @Test
    void givenNewFamilyAndToken_whenSaved_thenBothArePersistedAndMapped() {
        RefreshToken token = new RefreshToken(
            TOKEN_ID_1,
            FAMILY_ID_1,
            new RefreshTokenHash(HASH_1),
            CREATED_AT,
            TOKEN_EXPIRES_AT,
            null,
            null,
            null
        );
        RefreshTokenFamily family = new RefreshTokenFamily(
            FAMILY_ID_1,
            "squall",
            CREATED_AT,
            FAMILY_EXPIRES_AT,
            null,
            token
        );

        familyRepository.save(family);
        tokenRepository.save(token);

        assertThat(jdbcClient.sql("SELECT count(id) FROM refresh_token_family WHERE id = :id")
            .param("id", FAMILY_ID_1)
            .query(Long.class)
            .single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("SELECT count(id) FROM refresh_token WHERE id = :id")
            .param("id", TOKEN_ID_1)
            .query(Long.class)
            .single()).isEqualTo(1L);
        assertThat(familyRepository.findById(FAMILY_ID_1)).contains(family);
        assertThat(tokenRepository.findById(TOKEN_ID_1)).contains(token);
    }
}
