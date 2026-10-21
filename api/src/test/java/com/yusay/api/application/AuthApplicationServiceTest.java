package com.yusay.api.application;

import com.yusay.api.application.port.RefreshTokenLock;
import com.yusay.api.application.port.UserAuthoritiesProvider;
import com.yusay.api.config.JwtProperties;
import com.yusay.api.config.RefreshTokenProperties;
import com.yusay.api.domain.entity.RefreshToken;
import com.yusay.api.domain.entity.RefreshTokenFamily;
import com.yusay.api.domain.entity.vo.RefreshTokenHash;
import com.yusay.api.domain.repository.RefreshTokenFamilyRepository;
import com.yusay.api.domain.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {
    private static final String FAMILY_ID = "00000000-0000-0000-0000-000000000001";
    private static final String CURRENT_TOKEN_ID = "10000000-0000-0000-0000-000000000001";
    private static final String CONSUMED_TOKEN_ID = "10000000-0000-0000-0000-000000000002";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-21T12:00:00Z");
    private static final OffsetDateTime NOW = OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    @Mock
    AuthenticationManager authenticationManager;

    @Mock
    JwtEncoder jwtEncoder;

    @Mock
    RefreshTokenFamilyRepository familyRepository;

    @Mock
    RefreshTokenRepository tokenRepository;

    @Mock
    RefreshTokenLock refreshTokenLock;

    @Mock
    UserAuthoritiesProvider userAuthoritiesProvider;

    @Mock
    Jwt jwt;

    private AuthApplicationService authApplicationService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("test-issuer");
        jwtProperties.setTtl(Duration.ofMinutes(15));

        authApplicationService = new AuthApplicationService(
            authenticationManager,
            jwtEncoder,
            jwtProperties,
            new RefreshTokenProperties(Duration.ofHours(8), Duration.ofHours(24), 32),
            familyRepository,
            tokenRepository,
            refreshTokenLock,
            userAuthoritiesProvider,
            Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC),
            new SecureRandom()
        );

    }

    @Test
    void givenValidCredentials_whenSignIn_thenPersistsFamilyAndRefreshToken() {
        Authentication authentication = new TestingAuthenticationToken("cid", "password", "ROLE_USER");
        doReturn(authentication).when(authenticationManager).authenticate(any(Authentication.class));
        stubJwtEncoding();

        AuthApplicationService.TokenSet result = authApplicationService.signIn("cid", "password");

        ArgumentCaptor<RefreshTokenFamily> familyCaptor = ArgumentCaptor.forClass(RefreshTokenFamily.class);
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        InOrder saveOrder = inOrder(refreshTokenLock, familyRepository, tokenRepository);
        saveOrder.verify(refreshTokenLock).lockUsername("cid");
        saveOrder.verify(familyRepository).save(familyCaptor.capture());
        saveOrder.verify(tokenRepository).save(tokenCaptor.capture());

        RefreshTokenFamily savedFamily = familyCaptor.getValue();
        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(result.token()).isEqualTo("access-token");
        assertThat(result.username()).isEqualTo("cid");
        assertThat(result.authorities()).containsExactly("ROLE_USER");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(Duration.ofMinutes(15).toSeconds());
        assertThat(result.refreshToken()).hasSize(43);
        assertThat(savedFamily.id()).isEqualTo(savedToken.familyId());
        assertThat(savedFamily.createdAt()).isEqualTo(NOW);
        assertThat(savedFamily.absoluteExpiresAt()).isEqualTo(NOW.plusHours(24));
        assertThat(savedToken.issuedAt()).isEqualTo(NOW);
        assertThat(savedToken.expiresAt()).isEqualTo(NOW.plusHours(8));
        assertThat(savedToken.tokenHash()).isEqualTo(hash(result.refreshToken()));
    }

    @Test
    void givenActiveRefreshToken_whenRefresh_thenConsumesOldBeforeSavingReplacement() {
        String rawRefreshToken = "current-refresh-token";
        RefreshToken currentToken = new RefreshToken(
            CURRENT_TOKEN_ID,
            FAMILY_ID,
            hash(rawRefreshToken),
            NOW.minusHours(1),
            NOW.plusHours(7),
            null,
            null,
            null
        );
        RefreshTokenFamily family = new RefreshTokenFamily(
            FAMILY_ID,
            "cid",
            NOW.minusHours(1),
            NOW.plusHours(20),
            null,
            currentToken
        );
        doReturn(Optional.of(currentToken), Optional.of(currentToken))
            .when(tokenRepository).findByHash(currentToken.tokenHash());
        doReturn(Optional.of(family)).when(familyRepository).findById(FAMILY_ID);
        doReturn(List.of("ROLE_USER", "ROLE_ADMIN"))
            .when(userAuthoritiesProvider).findAuthorities("cid");
        stubJwtEncoding();

        AuthApplicationService.RefreshResult result = authApplicationService.refresh(rawRefreshToken);

        assertThat(result).isInstanceOf(AuthApplicationService.RefreshSucceeded.class);
        AuthApplicationService.TokenSet tokens =
            ((AuthApplicationService.RefreshSucceeded) result).tokens();
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        InOrder saveOrder = inOrder(tokenRepository);
        saveOrder.verify(tokenRepository, times(2)).save(tokenCaptor.capture());
        RefreshToken consumedToken = tokenCaptor.getAllValues().get(0);
        RefreshToken replacementToken = tokenCaptor.getAllValues().get(1);

        assertThat(consumedToken.id()).isEqualTo(CURRENT_TOKEN_ID);
        assertThat(consumedToken.consumedAt()).isEqualTo(NOW);
        assertThat(consumedToken.replacedById()).isEqualTo(replacementToken.id());
        assertThat(replacementToken.id()).isNotEqualTo(CURRENT_TOKEN_ID);
        assertThat(replacementToken.familyId()).isEqualTo(FAMILY_ID);
        assertThat(replacementToken.issuedAt()).isEqualTo(NOW);
        assertThat(replacementToken.expiresAt()).isEqualTo(NOW.plusHours(8));
        assertThat(replacementToken.tokenHash()).isEqualTo(hash(tokens.refreshToken()));
        assertThat(tokens.authorities()).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void givenConsumedRefreshToken_whenRefresh_thenRevokesFamilyAndReturnsReuseOutcome() {
        RefreshToken currentToken = new RefreshToken(
            CURRENT_TOKEN_ID,
            FAMILY_ID,
            hash("current-refresh-token"),
            NOW.minusMinutes(1),
            NOW.plusHours(7),
            null,
            null,
            null
        );
        RefreshToken consumedToken = new RefreshToken(
            CONSUMED_TOKEN_ID,
            FAMILY_ID,
            hash("previous-refresh-token"),
            NOW.minusHours(1),
            NOW.plusHours(6),
            NOW.minusMinutes(1),
            null,
            CURRENT_TOKEN_ID
        );
        RefreshTokenFamily family = new RefreshTokenFamily(
            FAMILY_ID,
            "cid",
            NOW.minusHours(1),
            NOW.plusHours(20),
            null,
            currentToken
        );
        doReturn(Optional.of(consumedToken), Optional.of(consumedToken))
            .when(tokenRepository).findByHash(consumedToken.tokenHash());
        doReturn(Optional.of(family)).when(familyRepository).findById(FAMILY_ID);

        AuthApplicationService.RefreshResult result =
            authApplicationService.refresh("previous-refresh-token");

        assertThat(result).isInstanceOf(AuthApplicationService.RefreshTokenReuseDetected.class);
        verify(familyRepository).save(org.mockito.ArgumentMatchers.argThat(
            revokedFamily -> revokedFamily.revokedAt().equals(NOW)
        ));
        verify(tokenRepository).save(org.mockito.ArgumentMatchers.argThat(
            token -> token.id().equals(CURRENT_TOKEN_ID) && token.isRevoked()
        ));
        verify(userAuthoritiesProvider, never()).findAuthorities(anyString());
        verify(jwtEncoder, never()).encode(any(JwtEncoderParameters.class));
    }

    private static RefreshTokenHash hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return new RefreshTokenHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void stubJwtEncoding() {
        doReturn("access-token").when(jwt).getTokenValue();
        doReturn(jwt).when(jwtEncoder).encode(any(JwtEncoderParameters.class));
    }
}