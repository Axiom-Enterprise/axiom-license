package com.github.axiomc.license.common.protocol;

public enum LicenseVerdict {
    VALID,
    UNKNOWN_KEY,
    REVOKED,
    EXPIRED,
    PRODUCT_MISMATCH,
    HARDWARE_MISMATCH;

    private static final LicenseVerdict[] VALUES = values();

    public boolean granted() {
        return this == VALID;
    }

    static LicenseVerdict of(byte code) {
        if (code < 0 || code >= VALUES.length) {
            throw new IllegalArgumentException("unknown verdict " + code);
        }
        return VALUES[code];
    }
}
