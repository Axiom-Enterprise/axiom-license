package com.github.axiomc.license.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.axiomc.license.common.domain.License;
import com.github.axiomc.license.common.domain.LicenseStatus;
import com.github.axiomc.license.common.persistence.Database;
import com.github.axiomc.license.common.persistence.LicenseRepository;
import com.github.axiomc.license.common.protocol.LicenseRequest;
import com.github.axiomc.license.common.protocol.LicenseVerdict;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    @TempDir
    Path dir;

    private Database database;
    private LicenseRepository licenses;
    private LicenseService service;

    @BeforeEach
    void open() throws Exception {
        database = Database.open(dir.resolve("test.db"));
        licenses = new LicenseRepository(database);
        service = new LicenseService(licenses, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void bindsOnFirstUseThenRefusesOtherHardware() {
        License license = licenses.insert(LicenseKeys.next(), "app", "carlo", null, NOW).join();
        assertEquals(LicenseVerdict.VALID, verdict(license.key(), "app", "hw-1"));
        assertEquals(LicenseVerdict.VALID, verdict(license.key(), "app", "hw-1"));
        assertEquals(LicenseVerdict.HARDWARE_MISMATCH, verdict(license.key(), "app", "hw-2"));
        assertEquals(LicenseVerdict.PRODUCT_MISMATCH, verdict(license.key(), "other", "hw-1"));
        assertEquals("hw-1", licenses.findByKey(license.key()).join().orElseThrow().hardwareId());
        assertTrue(licenses.bindHardware(license.id(), "hw-1", NOW).join());
        assertFalse(licenses.bindHardware(license.id(), "hw-2", NOW).join());
    }

    @Test
    void reportsRevokedExpiredAndUnknown() {
        License expired = licenses.insert(LicenseKeys.next(), "app", "x", NOW.minus(Duration.ofDays(1)), NOW).join();
        License revoked = licenses.insert(LicenseKeys.next(), "app", "y", null, NOW).join();
        licenses.setStatus(revoked.id(), LicenseStatus.REVOKED).join();
        assertEquals(LicenseVerdict.EXPIRED, verdict(expired.key(), "app", "hw"));
        assertEquals(LicenseVerdict.REVOKED, verdict(revoked.key(), "app", "hw"));
        assertEquals(LicenseVerdict.UNKNOWN_KEY, verdict("AXM-NOPE", "app", "hw"));
    }

    @Test
    void rejectsStaleClocks() {
        LicenseRequest stale = new LicenseRequest("AXM-1", "app", "hw", NOW.minus(Duration.ofMinutes(6)).toEpochMilli(), 1);
        assertThrows(CompletionException.class, () -> service.verify(stale).join());
    }

    private LicenseVerdict verdict(String key, String product, String hardware) {
        return service.verify(new LicenseRequest(key, product, hardware, NOW.toEpochMilli(), 7)).join().verdict();
    }
}
