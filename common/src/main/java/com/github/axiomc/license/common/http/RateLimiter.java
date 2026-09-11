package com.github.axiomc.license.common.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {

    private record Window(long startedAt, int count) {
    }

    private static final int MAX_TRACKED = 50_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMillis;

    public RateLimiter(int limit, long windowMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    public boolean allow(String key) {
        if (windows.size() >= MAX_TRACKED) {
            windows.clear();
        }
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (ignored, current) -> current == null || now - current.startedAt() >= windowMillis
                ? new Window(now, 1)
                : new Window(current.startedAt(), current.count() + 1));
        return window.count() <= limit;
    }
}
