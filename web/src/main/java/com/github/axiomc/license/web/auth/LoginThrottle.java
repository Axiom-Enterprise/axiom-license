package com.github.axiomc.license.web.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LoginThrottle {

    private record Window(int attempts, long resetAt) {
    }

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT = Duration.ofMinutes(15).toMillis();
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean blocked(String address, String username) {
        return blocked(bucket(address)) || blocked("user:" + username);
    }

    public void attempt(String address, String username) {
        if (windows.size() >= MAX_TRACKED) {
            windows.clear();
        }
        record(bucket(address));
        record("user:" + username);
    }

    public void succeeded(String address, String username) {
        windows.remove(bucket(address));
        windows.remove("user:" + username);
    }

    private boolean blocked(String key) {
        Window window = windows.get(key);
        if (window == null) {
            return false;
        }
        if (window.resetAt() < System.currentTimeMillis()) {
            windows.remove(key);
            return false;
        }
        return window.attempts() >= MAX_ATTEMPTS;
    }

    private void record(String key) {
        long now = System.currentTimeMillis();
        windows.compute(key, (ignored, window) -> window == null || window.resetAt() < now
                ? new Window(1, now + LOCKOUT)
                : new Window(window.attempts() + 1, window.resetAt()));
    }

    private static String bucket(String address) {
        if (address.indexOf(':') < 0) {
            return "ip:" + address;
        }
        try {
            return "net:" + HexFormat.of().formatHex(InetAddress.getByName(address).getAddress(), 0, 8);
        } catch (UnknownHostException e) {
            return "ip:" + address;
        }
    }
}
