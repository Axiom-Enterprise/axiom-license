package com.github.axiomc.license.common.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class Envelope {

    public record Sealed(byte[] wire, SessionKeys keys) {
    }

    public record Opened(byte[] plaintext, SessionKeys keys) {
    }

    private static final byte VERSION = 1;
    private static final int PUBLIC_LENGTH = 44;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int SIGNATURE_LENGTH = 64;
    private static final byte[] INFO = "axiom-license/v1".getBytes();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Envelope() {
    }

    public static Sealed sealRequest(PublicKey serverExchange, byte[] plaintext) throws GeneralSecurityException {
        KeyPair ephemeral = KeyPairGenerator.getInstance(ServerKeys.EXCHANGE_ALGORITHM).generateKeyPair();
        byte[] ephemeralPublic = ephemeral.getPublic().getEncoded();
        SessionKeys keys = agree(ephemeral.getPrivate(), serverExchange, ephemeralPublic, serverExchange.getEncoded());
        byte[] aad = header(ephemeralPublic);
        byte[] nonce = nonce();
        byte[] sealed = cipher(Cipher.ENCRYPT_MODE, keys.request(), nonce, aad).doFinal(plaintext);
        ByteBuffer wire = ByteBuffer.allocate(aad.length + NONCE_LENGTH + sealed.length);
        wire.put(aad).put(nonce).put(sealed);
        return new Sealed(wire.array(), keys);
    }

    public static Opened openRequest(KeyPair serverExchange, byte[] wire) throws GeneralSecurityException {
        if (wire.length < 1 + PUBLIC_LENGTH + NONCE_LENGTH + TAG_BITS / 8 || wire[0] != VERSION) {
            throw new GeneralSecurityException("malformed request envelope");
        }
        ByteBuffer in = ByteBuffer.wrap(wire);
        byte[] aad = new byte[1 + PUBLIC_LENGTH];
        byte[] nonce = new byte[NONCE_LENGTH];
        in.get(aad).get(nonce);
        byte[] sealed = new byte[in.remaining()];
        in.get(sealed);
        byte[] ephemeralPublic = new byte[PUBLIC_LENGTH];
        System.arraycopy(aad, 1, ephemeralPublic, 0, PUBLIC_LENGTH);
        PublicKey clientPublic = KeyFactory.getInstance(ServerKeys.EXCHANGE_ALGORITHM).generatePublic(new X509EncodedKeySpec(ephemeralPublic));
        SessionKeys keys = agree(serverExchange.getPrivate(), clientPublic, ephemeralPublic, serverExchange.getPublic().getEncoded());
        byte[] plaintext = cipher(Cipher.DECRYPT_MODE, keys.request(), nonce, aad).doFinal(sealed);
        return new Opened(plaintext, keys);
    }

    public static byte[] sealResponse(SessionKeys keys, PrivateKey signing, byte[] plaintext) throws GeneralSecurityException {
        Signature signer = Signature.getInstance(ServerKeys.SIGNING_ALGORITHM);
        signer.initSign(signing);
        signer.update(plaintext);
        byte[] signature = signer.sign();
        byte[] body = ByteBuffer.allocate(plaintext.length + SIGNATURE_LENGTH).put(plaintext).put(signature).array();
        byte[] nonce = nonce();
        byte[] sealed = cipher(Cipher.ENCRYPT_MODE, keys.response(), nonce, keys.ephemeralPublic()).doFinal(body);
        return ByteBuffer.allocate(1 + NONCE_LENGTH + sealed.length).put(VERSION).put(nonce).put(sealed).array();
    }

    public static byte[] openResponse(SessionKeys keys, PublicKey signing, byte[] wire) throws GeneralSecurityException {
        if (wire.length < 1 + NONCE_LENGTH + SIGNATURE_LENGTH + TAG_BITS / 8 || wire[0] != VERSION) {
            throw new GeneralSecurityException("malformed response envelope");
        }
        ByteBuffer in = ByteBuffer.wrap(wire, 1, wire.length - 1);
        byte[] nonce = new byte[NONCE_LENGTH];
        in.get(nonce);
        byte[] sealed = new byte[in.remaining()];
        in.get(sealed);
        byte[] body = cipher(Cipher.DECRYPT_MODE, keys.response(), nonce, keys.ephemeralPublic()).doFinal(sealed);
        int plaintextLength = body.length - SIGNATURE_LENGTH;
        Signature verifier = Signature.getInstance(ServerKeys.SIGNING_ALGORITHM);
        verifier.initVerify(signing);
        verifier.update(body, 0, plaintextLength);
        if (!verifier.verify(body, plaintextLength, SIGNATURE_LENGTH)) {
            throw new GeneralSecurityException("response signature mismatch");
        }
        byte[] plaintext = new byte[plaintextLength];
        System.arraycopy(body, 0, plaintext, 0, plaintextLength);
        return plaintext;
    }

    private static SessionKeys agree(PrivateKey own, PublicKey peer, byte[] ephemeralPublic, byte[] serverPublic) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance(ServerKeys.EXCHANGE_ALGORITHM);
        agreement.init(own);
        agreement.doPhase(peer, true);
        byte[] salt = ByteBuffer.allocate(ephemeralPublic.length + serverPublic.length).put(ephemeralPublic).put(serverPublic).array();
        byte[] material = Hkdf.derive(agreement.generateSecret(), salt, INFO, 64);
        byte[] request = new byte[32];
        byte[] response = new byte[32];
        System.arraycopy(material, 0, request, 0, 32);
        System.arraycopy(material, 32, response, 0, 32);
        return new SessionKeys(request, response, ephemeralPublic);
    }

    private static byte[] header(byte[] ephemeralPublic) {
        return ByteBuffer.allocate(1 + PUBLIC_LENGTH).put(VERSION).put(ephemeralPublic).array();
    }

    private static byte[] nonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        return nonce;
    }

    private static Cipher cipher(int mode, byte[] key, byte[] nonce, byte[] aad) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher;
    }
}
