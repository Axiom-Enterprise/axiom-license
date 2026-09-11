package com.github.axiomc.license.web.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    @Test
    void locksAddressBucketAndUsernameAfterFiveAttempts() {
        LoginThrottle throttle = new LoginThrottle();
        for (int i = 0; i < 5; i++) {
            assertFalse(throttle.blocked("2001:db8:1:2::" + i, "admin"));
            throttle.attempt("2001:db8:1:2::" + i, "admin");
        }
        assertTrue(throttle.blocked("2001:db8:1:2::ffff", "someone"));
        assertTrue(throttle.blocked("203.0.113.9", "admin"));
        assertFalse(throttle.blocked("203.0.113.9", "other"));
        throttle.succeeded("2001:db8:1:2::1", "admin");
        assertFalse(throttle.blocked("203.0.113.9", "admin"));
    }
}
