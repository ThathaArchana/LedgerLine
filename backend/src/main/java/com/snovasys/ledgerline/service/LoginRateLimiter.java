package com.snovasys.ledgerline.service;

public interface LoginRateLimiter {
    void ensureAllowed(String email);
    void recordFailure(String email);
    void clear(String email);
}