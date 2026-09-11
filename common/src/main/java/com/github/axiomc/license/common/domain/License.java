package com.github.axiomc.license.common.domain;

import java.time.Instant;

public record License(long id, String key, String product, String holder, String hardwareId, Instant expiresAt, LicenseStatus status, Instant createdAt, Instant lastSeenAt) {

    public boolean expired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
