package com.github.axiomc.license.web.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecurityFilterTest {

    @Test
    void adminCoversEverythingModCoversOnlyMod() {
        assertTrue(Role.ADMIN.covers(Role.ADMIN));
        assertTrue(Role.ADMIN.covers(Role.MOD));
        assertTrue(Role.MOD.covers(Role.MOD));
        assertFalse(Role.MOD.covers(Role.ADMIN));
    }

    @Test
    void sessionsExpireAndCloseForAnAccount() {
        SessionRegistry registry = new SessionRegistry();
        Account account = new Account(1, "admin", Role.ADMIN, java.time.Instant.EPOCH);
        String token = registry.open(account);
        assertTrue(registry.resolve(token).isPresent());
        registry.closeAll(1);
        assertFalse(registry.resolve(token).isPresent());
        assertFalse(registry.resolve("nope").isPresent());
    }
}
