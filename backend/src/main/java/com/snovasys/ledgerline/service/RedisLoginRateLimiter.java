package com.snovasys.ledgerline.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(name = "ledgerline.redis.enabled", havingValue = "true")
public class RedisLoginRateLimiter implements LoginRateLimiter {
    private static final long MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final StringRedisTemplate redisTemplate;

    public RedisLoginRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void ensureAllowed(String email) {
        String failures = redisTemplate.opsForValue().get(key(email));
        if (failures != null && Long.parseLong(failures) >= MAX_FAILURES) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Try again later.");
        }
    }

    @Override
    public void recordFailure(String email) {
        String key = key(email);
        Long failures = redisTemplate.opsForValue().increment(key);
        if (failures != null && failures == 1) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    @Override
    public void clear(String email) {
        redisTemplate.delete(key(email));
    }

    private String key(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8));
            return "ledgerline:login-failures:" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not derive rate-limit key.", exception);
        }
    }
}