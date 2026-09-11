package com.github.axiomc.license.common.api;

import com.github.axiomc.license.common.domain.License;
import com.github.axiomc.license.common.domain.LicenseStatus;
import com.github.axiomc.license.common.persistence.LicenseRepository;
import com.github.axiomc.license.common.protocol.LicenseRequest;
import com.github.axiomc.license.common.protocol.LicenseResponse;
import com.github.axiomc.license.common.protocol.LicenseVerdict;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class LicenseService {

    static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final LicenseRepository licenses;
    private final Clock clock;

    public LicenseService(LicenseRepository licenses, Clock clock) {
        this.licenses = licenses;
        this.clock = clock;
    }

    public CompletableFuture<LicenseResponse> verify(LicenseRequest request) {
        Instant now = clock.instant();
        if (Math.abs(now.toEpochMilli() - request.issuedAt()) > CLOCK_SKEW.toMillis()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("request clock out of range"));
        }
        return licenses.findByKey(LicenseKeys.normalise(request.key())).thenCompose(found -> decide(found, request, now));
    }

    private CompletableFuture<LicenseResponse> decide(Optional<License> found, LicenseRequest request, Instant now) {
        if (found.isEmpty()) {
            return CompletableFuture.completedFuture(response(LicenseVerdict.UNKNOWN_KEY, request, now, null));
        }
        License license = found.get();
        LicenseVerdict verdict = judge(license, request, now);
        if (!verdict.granted()) {
            return CompletableFuture.completedFuture(response(verdict, request, now, null));
        }
        if (license.hardwareId() != null) {
            licenses.touch(license.id(), now);
            return CompletableFuture.completedFuture(response(LicenseVerdict.VALID, request, now, license));
        }
        return licenses.bindHardware(license.id(), request.hardwareId(), now).thenApply(ok -> response(ok ? LicenseVerdict.VALID : LicenseVerdict.HARDWARE_MISMATCH, request, now, license));
    }

    private static LicenseVerdict judge(License license, LicenseRequest request, Instant now) {
        return switch (license.status()) {
            case REVOKED -> LicenseVerdict.REVOKED;
            case ACTIVE -> {
                if (license.expired(now)) {
                    yield LicenseVerdict.EXPIRED;
                }
                if (!license.product().equals(request.product())) {
                    yield LicenseVerdict.PRODUCT_MISMATCH;
                }
                if (license.hardwareId() != null && !license.hardwareId().equals(request.hardwareId())) {
                    yield LicenseVerdict.HARDWARE_MISMATCH;
                }
                yield LicenseVerdict.VALID;
            }
        };
    }

    private static LicenseResponse response(LicenseVerdict verdict, LicenseRequest request, Instant now, License license) {
        long expiresAt = verdict.granted() && license != null && license.expiresAt() != null ? license.expiresAt().toEpochMilli() : 0L;
        return new LicenseResponse(verdict, request.nonce(), now.toEpochMilli(), expiresAt);
    }
}
