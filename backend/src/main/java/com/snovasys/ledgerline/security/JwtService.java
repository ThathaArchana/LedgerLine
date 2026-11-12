package com.snovasys.ledgerline.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snovasys.ledgerline.entity.User;
import com.snovasys.ledgerline.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long ttlSeconds;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${ledgerline.jwt.secret:local-only-change-this-secret-before-deploying-123456}") String secret,
            @Value("${ledgerline.jwt.ttl-seconds:3600}") long ttlSeconds) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes.");
        }
    }

    public String issue(User user) {
        long issuedAt = Instant.now().getEpochSecond();
        Map<String, Object> claims = Map.of(
                "sub", user.getEmail(),
                "uid", user.getId(),
                "role", user.getRole().name(),
                "iat", issuedAt,
                "exp", issuedAt + ttlSeconds);
        try {
            String header = ENCODER.encodeToString(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = ENCODER.encodeToString(objectMapper.writeValueAsBytes(claims));
            String signedContent = header + "." + payload;
            return signedContent + "." + ENCODER.encodeToString(sign(signedContent));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create access token.", exception);
        }
    }

    public UserPrincipal verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token.");
            }
            String signedContent = parts[0] + "." + parts[1];
            if (!java.security.MessageDigest.isEqual(sign(signedContent), DECODER.decode(parts[2]))) {
                throw new IllegalArgumentException("Invalid token signature.");
            }
            Map<String, Object> claims = objectMapper.readValue(DECODER.decode(parts[1]), new TypeReference<>() { });
            long expiresAt = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw new IllegalArgumentException("Token expired.");
            }
            return new UserPrincipal(
                    ((Number) claims.get("uid")).longValue(),
                    (String) claims.get("sub"),
                    UserRole.valueOf((String) claims.get("role")));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid or expired access token.", exception);
        }
    }

    private byte[] sign(String content) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(secret, ALGORITHM));
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }
}