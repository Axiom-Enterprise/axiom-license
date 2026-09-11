package com.github.axiomc.license.common.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void allowsUpToLimitPerKeyWithinWindow() {
        RateLimiter limiter = new RateLimiter(3, 60_000);
        assertTrue(limiter.allow("a"));
        assertTrue(limiter.allow("a"));
        assertTrue(limiter.allow("a"));
        assertFalse(limiter.allow("a"));
        assertTrue(limiter.allow("b"));
    }
}
