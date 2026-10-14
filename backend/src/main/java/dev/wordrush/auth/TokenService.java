package dev.wordrush.auth;

import dev.wordrush.config.WordRushProperties;
import dev.wordrush.user.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Mints access tokens. Verification is Spring Security's job (see {@link JwtConfig#jwtDecoder}). */
@Service
public class TokenService {

    public record IssuedToken(String value, Instant expiresAt) {
    }

    private final JwtEncoder encoder;
    private final WordRushProperties.Jwt config;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, WordRushProperties properties, Clock clock) {
        this.encoder = encoder;
        this.config = properties.jwt();
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(config.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(config.issuer())
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(AuthenticatedUser.CLAIM_NICKNAME, user.nickname())
                .claim(AuthenticatedUser.CLAIM_BOT, user.isBot())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
