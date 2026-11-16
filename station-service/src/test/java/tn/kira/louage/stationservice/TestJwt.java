package tn.kira.louage.stationservice;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

final class TestJwt {

    private static final String SECRET = "louage-tracker-dev-secret-key-32-chars-min";
    private static final JwtEncoder ENCODER =
            new NimbusJwtEncoder(new ImmutableSecret<>(SECRET.getBytes(StandardCharsets.UTF_8)));

    private TestJwt() {
    }

    static String admin() {
        return token("admin", "ADMIN");
    }

    static String driver() {
        return token("driver", "DRIVER");
    }

    static String passenger() {
        return token("passenger", "PASSENGER");
    }

    static String token(String username, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        return ENCODER.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
