package com.github.axiomc.license.web.auth;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LoginThrottle {

    private record Window(int failures, long resetAt) {
    }

    private static final int MAX_FAILURES = 5;
    private static final long LOCKOUT = Duration.ofMinutes(15).toMillis();
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean blocked(String address) {
        Window window = windows.get(address);
        if (window == null) {
            return false;
        }
        if (window.resetAt() < System.currentTimeMillis()) {
            windows.remove(address);
            return false;
        }
        return window.failures() >= MAX_FAILURES;
    }

    public void failed(String address) {
        if (windows.size() >= MAX_TRACKED) {
            windows.clear();
        }
        long now = System.currentTimeMillis();
        windows.compute(address, (ignored, window) -> window == null || window.resetAt() < now
                ? new Window(1, now + LOCKOUT)
                : new Window(window.failures() + 1, window.resetAt()));
    }

    public void succeeded(String address) {
        windows.remove(address);
    }
}
