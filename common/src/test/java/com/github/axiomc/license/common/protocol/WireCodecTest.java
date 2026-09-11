package com.github.axiomc.license.common.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WireCodecTest {

    @Test
    void requestAndResponseSurviveEncoding() {
        LicenseRequest request = new LicenseRequest("AXM-1", "prodüct", "hw-ß", 1_700_000_000_000L, -42L);
        assertEquals(request, LicenseRequest.decode(request.encode()));

        LicenseResponse response = new LicenseResponse(LicenseVerdict.HARDWARE_MISMATCH, -42L, 5L, 0L);
        assertEquals(response, LicenseResponse.decode(response.encode()));
    }

    @Test
    void rejectsOversizedAndMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> new LicenseRequest("x".repeat(300), "p", "h", 0, 0).encode());
        assertThrows(IllegalArgumentException.class, () -> LicenseRequest.decode(new byte[]{0, 5, 1}));
        assertThrows(IllegalArgumentException.class, () -> LicenseRequest.decode(new byte[6]));
        assertThrows(IllegalArgumentException.class, () -> LicenseResponse.decode(new byte[]{99, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
    }
}
