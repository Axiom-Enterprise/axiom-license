package com.github.axiomc.license.web.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionRegistry {

    public static final String COOKIE = "axiom_session";

    private record Session(Account account, Instant expiresAt) {
    }

    public static final long LIFETIME_SECONDS = Duration.ofHours(8).toSeconds();

    private static final Duration LIFETIME = Duration.ofSeconds(LIFETIME_SECONDS);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String open(Account account) {
        sweep();
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        sessions.put(id, new Session(account, Instant.now().plus(LIFETIME)));
        return id;
    }

    public Optional<Account> resolve(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.account());
    }

    public void close(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public void closeAll(long accountId) {
        Iterator<Session> it = sessions.values().iterator();
        while (it.hasNext()) {
            if (it.next().account().id() == accountId) {
                it.remove();
            }
        }
    }

    private void sweep() {
        Instant now = Instant.now();
        Iterator<Session> it = sessions.values().iterator();
        while (it.hasNext()) {
            if (it.next().expiresAt().isBefore(now)) {
                it.remove();
            }
        }
    }
}
