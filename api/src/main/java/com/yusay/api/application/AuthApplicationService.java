package com.yusay.api.application;

import com.yusay.api.application.port.RefreshTokenLock;
import com.yusay.api.application.port.UserAuthoritiesProvider;
import com.yusay.api.config.JwtProperties;
import com.yusay.api.config.RefreshTokenProperties;
import com.yusay.api.domain.entity.RefreshToken;
import com.yusay.api.domain.entity.RefreshTokenFamily;
import com.yusay.api.domain.entity.UserInfo;
import com.yusay.api.domain.entity.vo.RefreshTokenHash;
import com.yusay.api.domain.repository.RefreshTokenFamilyRepository;
import com.yusay.api.domain.repository.RefreshTokenRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

@Service
public class AuthApplicationService {
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final RefreshTokenProperties refreshTokenProperties;
    private final RefreshTokenFamilyRepository familyRepository;
    private final RefreshTokenRepository tokenRepository;
    private final RefreshTokenLock refreshTokenLock;
    private final UserAuthoritiesProvider userAuthoritiesProvider;
    private final Clock clock;
    private final RandomGenerator randomGenerator;

    public AuthApplicationService(
        AuthenticationManager authenticationManager,
        JwtEncoder jwtEncoder,
        JwtProperties jwtProperties,
        RefreshTokenProperties refreshTokenProperties,
        RefreshTokenFamilyRepository familyRepository,
        RefreshTokenRepository tokenRepository,
        RefreshTokenLock refreshTokenLock,
        UserAuthoritiesProvider userAuthoritiesProvider,
        Clock clock,
        RandomGenerator randomGenerator
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.refreshTokenProperties = refreshTokenProperties;
        this.familyRepository = familyRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenLock = refreshTokenLock;
        this.userAuthoritiesProvider = userAuthoritiesProvider;
        this.clock = clock;
        this.randomGenerator = randomGenerator;
    }

    @Transactional
    @PreAuthorize("permitAll()")
    public TokenSet signIn(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(username, password)
        );
        String authenticatedUsername = authentication.getName();
        List<String> authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();
        OffsetDateTime now = now();
        String familyId = UUID.randomUUID().toString();
        OffsetDateTime absoluteExpiresAt = now.plus(refreshTokenProperties.absoluteTimeout());
        GeneratedRefreshToken generated = generateRefreshToken(familyId, now, absoluteExpiresAt);
        RefreshTokenFamily family = new RefreshTokenFamily(
            familyId,
            authenticatedUsername,
            now,
            absoluteExpiresAt,
            null,
            generated.token()
        );

        refreshTokenLock.lockUsername(authenticatedUsername);
        familyRepository.save(family);
        tokenRepository.save(generated.token());

        return createTokenSet(authenticatedUsername, authorities, generated.rawValue(), now.toInstant());
    }

    @Transactional
    @PreAuthorize("permitAll()")
    public RefreshResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return new RefreshRejected();
        }

        RefreshTokenHash presentedHash = hash(rawRefreshToken);
        RefreshToken tokenBeforeLock = tokenRepository.findByHash(presentedHash).orElse(null);
        if (tokenBeforeLock == null) {
            return new RefreshRejected();
        }

        refreshTokenLock.lockFamily(tokenBeforeLock.familyId());
        RefreshToken presentedToken = tokenRepository.findByHash(presentedHash).orElse(null);
        RefreshTokenFamily family = familyRepository.findById(tokenBeforeLock.familyId()).orElse(null);
        if (presentedToken == null || family == null) {
            return new RefreshRejected();
        }

        OffsetDateTime now = now();
        if (!family.isActiveAt(now)) {
            return new RefreshRejected();
        }
        GeneratedRefreshToken generated = generateRefreshToken(
            family.id(),
            now,
            family.absoluteExpiresAt()
        );
        RefreshTokenFamily.RotationResult rotation = family.rotate(
            presentedToken,
            generated.token(),
            now
        );

        if (rotation instanceof RefreshTokenFamily.TokenReuseDetected(RefreshTokenFamily revokedFamily)) {
            familyRepository.save(revokedFamily);
            tokenRepository.save(revokedFamily.currentToken());
            return new RefreshTokenReuseDetected();
        }
        if (rotation instanceof RefreshTokenFamily.RotationRejected) {
            return new RefreshRejected();
        }

        RefreshTokenFamily.RotationAccepted accepted =
            (RefreshTokenFamily.RotationAccepted) rotation;
        List<String> authorities = userAuthoritiesProvider.findAuthorities(family.username());
        TokenSet tokenSet = createTokenSet(
            family.username(),
            authorities,
            generated.rawValue(),
            now.toInstant()
        );

        tokenRepository.save(accepted.consumedToken());
        tokenRepository.save(accepted.replacementToken());

        return new RefreshSucceeded(tokenSet);
    }

    @Transactional
    @PreAuthorize("permitAll()")
    public void signOut(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        RefreshTokenHash tokenHash = hash(rawRefreshToken);
        RefreshToken tokenBeforeLock = tokenRepository.findByHash(tokenHash).orElse(null);
        if (tokenBeforeLock == null) {
            return;
        }

        refreshTokenLock.lockFamily(tokenBeforeLock.familyId());
        RefreshTokenFamily family = familyRepository.findById(tokenBeforeLock.familyId()).orElse(null);
        if (family == null) {
            return;
        }

        RefreshTokenFamily revokedFamily = family.revoke(now());
        familyRepository.save(revokedFamily);
        tokenRepository.save(revokedFamily.currentToken());
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void signOutAll(String username, Authentication authentication) {
        if (authentication == null || !username.equals(authentication.getName())) {
            throw new AccessDeniedException("Cannot sign out sessions for another user");
        }

        refreshTokenLock.lockUsername(username);
        List<RefreshTokenFamily> families = familyRepository.findAllActiveByUsername(username, now());

        for (RefreshTokenFamily familySnapshot : families) {
            refreshTokenLock.lockFamily(familySnapshot.id());
            familyRepository.findById(familySnapshot.id()).ifPresent(family -> {
                OffsetDateTime currentTime = now();
                if (!family.isActiveAt(currentTime)) {
                    return;
                }
                RefreshTokenFamily revokedFamily = family.revoke(currentTime);
                familyRepository.save(revokedFamily);
                tokenRepository.save(revokedFamily.currentToken());
            });
        }
    }

    @PreAuthorize("permitAll()")
    public String issueToken(String username, List<String> authorities) {
        return createAccessToken(username, authorities, clock.instant());
    }

    @PreAuthorize("isAuthenticated()")
    public UserInfo userInfo(Jwt jwt) {
        return new UserInfo(jwt.getSubject(), jwt.getClaimAsStringList("authorities"));
    }

    private TokenSet createTokenSet(
        String username,
        List<String> authorities,
        String rawRefreshToken,
        Instant issuedAt
    ) {
        String accessToken = createAccessToken(username, authorities, issuedAt);

        return new TokenSet(
            accessToken,
            rawRefreshToken,
            username,
            List.copyOf(authorities),
            "Bearer",
            jwtProperties.getTtl().toSeconds()
        );
    }

    private String createAccessToken(String username, List<String> authorities, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(jwtProperties.getTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.getIssuer())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(username)
            .id(UUID.randomUUID().toString())
            .claim("authorities", authorities)
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private GeneratedRefreshToken generateRefreshToken(
        String familyId,
        OffsetDateTime issuedAt,
        OffsetDateTime absoluteExpiresAt
    ) {
        byte[] randomBytes = new byte[refreshTokenProperties.tokenLengthBytes()];
        randomGenerator.nextBytes(randomBytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        OffsetDateTime idleExpiresAt = issuedAt.plus(refreshTokenProperties.idleTimeout());
        OffsetDateTime expiresAt = idleExpiresAt.isBefore(absoluteExpiresAt)
            ? idleExpiresAt
            : absoluteExpiresAt;
        RefreshToken token = new RefreshToken(
            UUID.randomUUID().toString(),
            familyId,
            hash(rawValue),
            issuedAt,
            expiresAt,
            null,
            null,
            null
        );
        return new GeneratedRefreshToken(rawValue, token);
    }

    private RefreshTokenHash hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return new RefreshTokenHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private record GeneratedRefreshToken(String rawValue, RefreshToken token) {
    }

    public record TokenSet(
        String token,
        String refreshToken,
        String username,
        List<String> authorities,
        String tokenType,
        long expiresIn
    ) {
        public TokenSet {
            authorities = List.copyOf(authorities);
        }
    }

    public sealed interface RefreshResult
        permits RefreshSucceeded, RefreshRejected, RefreshTokenReuseDetected {
    }

    public record RefreshSucceeded(TokenSet tokens) implements RefreshResult {
    }

    public record RefreshRejected() implements RefreshResult {
    }

    public record RefreshTokenReuseDetected() implements RefreshResult {
    }
}
