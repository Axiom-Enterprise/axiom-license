package com.github.axiomc.license.common.crypto;

import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class Hkdf {

    private static final String MAC = "HmacSHA256";

    private Hkdf() {
    }

    static byte[] derive(byte[] ikm, byte[] salt, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(MAC);
        mac.init(new SecretKeySpec(salt, MAC));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, MAC));
        byte[] out = new byte[length];
        byte[] block = new byte[0];
        int filled = 0;
        for (byte counter = 1; filled < length; counter++) {
            mac.update(block);
            mac.update(info);
            mac.update(counter);
            block = mac.doFinal();
            int n = Math.min(block.length, length - filled);
            System.arraycopy(block, 0, out, filled, n);
            filled += n;
        }
        return out;
    }
}
