package com.github.axiomc.license.common.protocol;

import java.nio.ByteBuffer;

public record LicenseRequest(String key, String product, String hardwareId, long issuedAt, long nonce) {

    public LicenseRequest {
        if (key.isBlank() || product.isBlank() || hardwareId.isBlank()) {
            throw new IllegalArgumentException("key, product and hardwareId are required");
        }
    }

    public byte[] encode() {
        ByteBuffer out = ByteBuffer.allocate(Wire.sizeOf(key) + Wire.sizeOf(product) + Wire.sizeOf(hardwareId) + 2 * Long.BYTES);
        Wire.putString(out, key);
        Wire.putString(out, product);
        Wire.putString(out, hardwareId);
        out.putLong(issuedAt).putLong(nonce);
        return out.array();
    }

    public static LicenseRequest decode(byte[] bytes) {
        ByteBuffer in = ByteBuffer.wrap(bytes);
        String key = Wire.getString(in);
        String product = Wire.getString(in);
        String hardwareId = Wire.getString(in);
        if (in.remaining() != 2 * Long.BYTES) {
            throw new IllegalArgumentException("request length mismatch");
        }
        return new LicenseRequest(key, product, hardwareId, in.getLong(), in.getLong());
    }
}
