package com.github.axiomc.license.common.api;

import java.security.SecureRandom;

public final class LicenseKeys {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int GROUPS = 4;
    private static final int GROUP_LENGTH = 5;

    private LicenseKeys() {
    }

    public static String next() {
        StringBuilder key = new StringBuilder("AXM");
        for (int group = 0; group < GROUPS; group++) {
            key.append('-');
            for (int i = 0; i < GROUP_LENGTH; i++) {
                key.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            }
        }
        return key.toString();
    }

    public static String normalise(String raw) {
        return raw.strip().toUpperCase();
    }
}
