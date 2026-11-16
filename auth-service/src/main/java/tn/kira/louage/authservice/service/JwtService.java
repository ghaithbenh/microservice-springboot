package tn.kira.louage.authservice.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import tn.kira.louage.authservice.entity.AppUser;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final long expirySeconds;

    public JwtService(JwtEncoder encoder,
            @Value("${app.jwt.expiry-seconds:3600}") long expirySeconds) {
        this.encoder = encoder;
        this.expirySeconds = expirySeconds;
    }

    public String generateToken(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirySeconds))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }
}
