package com.github.axiomc.license.web.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void verifiesOnlyTheOriginalPassword() {
        String stored = PasswordHasher.hash("correct horse battery".toCharArray());
        assertTrue(PasswordHasher.verify("correct horse battery".toCharArray(), stored));
        assertFalse(PasswordHasher.verify("correct horse batterx".toCharArray(), stored));
        assertFalse(PasswordHasher.verify("anything".toCharArray(), null));
        assertFalse(PasswordHasher.verify("anything".toCharArray(), "garbage"));
    }
}
