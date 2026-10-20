package com.yusay.api.application;

import com.yusay.api.config.JwtProperties;
import com.yusay.api.domain.UserInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

@Service
public class AuthApplicationService {
    private final JwtEncoder encoder;
    private final JwtProperties props;

    public AuthApplicationService(JwtEncoder encoder, JwtProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    @PreAuthorize("permitAll()")
    public String issueToken(String username, Collection<String> authorities) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.getIssuer())
                .issuedAt(now)
                .expiresAt(now.plus(props.getTtl()))
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim("authorities", new ArrayList<>(authorities))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @PreAuthorize("isAuthenticated()")
    public UserInfo userInfo(Jwt jwt) {
        return new UserInfo(jwt.getSubject(), jwt.getClaimAsStringList("authorities"));
    }
}
