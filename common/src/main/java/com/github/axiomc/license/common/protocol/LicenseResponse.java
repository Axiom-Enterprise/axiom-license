package com.github.axiomc.license.common.protocol;

import java.nio.ByteBuffer;

public record LicenseResponse(LicenseVerdict verdict, long nonce, long serverTime, long expiresAt) {

    private static final int SIZE = Byte.BYTES + 3 * Long.BYTES;

    public byte[] encode() {
        return ByteBuffer.allocate(SIZE).put((byte) verdict.ordinal()).putLong(nonce).putLong(serverTime).putLong(expiresAt).array();
    }

    public static LicenseResponse decode(byte[] bytes) {
        if (bytes.length != SIZE) {
            throw new IllegalArgumentException("response must be " + SIZE + " bytes");
        }
        ByteBuffer in = ByteBuffer.wrap(bytes);
        return new LicenseResponse(LicenseVerdict.of(in.get()), in.getLong(), in.getLong(), in.getLong());
    }
}
