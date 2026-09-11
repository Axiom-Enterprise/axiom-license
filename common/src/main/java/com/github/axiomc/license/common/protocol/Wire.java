package com.github.axiomc.license.common.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class Wire {

    static final int MAX_STRING = 256;

    private Wire() {
    }

    static void putString(ByteBuffer out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING) {
            throw new IllegalArgumentException("string exceeds " + MAX_STRING + " bytes");
        }
        out.putShort((short) bytes.length).put(bytes);
    }

    static String getString(ByteBuffer in) {
        int length = Short.toUnsignedInt(in.getShort());
        if (length > MAX_STRING || length > in.remaining()) {
            throw new IllegalArgumentException("string length out of range");
        }
        byte[] bytes = new byte[length];
        in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static int sizeOf(String value) {
        return Short.BYTES + value.getBytes(StandardCharsets.UTF_8).length;
    }
}
