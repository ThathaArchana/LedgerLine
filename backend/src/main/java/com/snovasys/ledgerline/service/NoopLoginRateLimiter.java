package com.snovasys.ledgerline.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ledgerline.redis.enabled", havingValue = "false", matchIfMissing = true)
public class NoopLoginRateLimiter implements LoginRateLimiter {
    @Override
    public void ensureAllowed(String email) { }

    @Override
    public void recordFailure(String email) { }

    @Override
    public void clear(String email) { }
}