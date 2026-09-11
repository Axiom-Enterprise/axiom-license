package com.github.axiomc.license.common.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.GeneralSecurityException;
import org.junit.jupiter.api.Test;

class EnvelopeTest {

    @Test
    void roundTripsBothDirections() throws Exception {
        ServerKeys server = ServerKeys.generate();
        byte[] request = "hello".getBytes();
        Envelope.Sealed sealed = Envelope.sealRequest(server.exchange().getPublic(), request);
        Envelope.Opened opened = Envelope.openRequest(server.exchange(), sealed.wire());
        assertArrayEquals(request, opened.plaintext());

        byte[] reply = "world".getBytes();
        byte[] wire = Envelope.sealResponse(opened.keys(), server.signing().getPrivate(), reply);
        assertArrayEquals(reply, Envelope.openResponse(sealed.keys(), server.signing().getPublic(), wire));
    }

    @Test
    void rejectsTamperingAndForeignSigner() throws Exception {
        ServerKeys server = ServerKeys.generate();
        ServerKeys impostor = ServerKeys.generate();
        Envelope.Sealed sealed = Envelope.sealRequest(server.exchange().getPublic(), new byte[]{1, 2, 3});
        byte[] tampered = sealed.wire().clone();
        tampered[tampered.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> Envelope.openRequest(server.exchange(), tampered));
        assertThrows(GeneralSecurityException.class, () -> Envelope.openRequest(impostor.exchange(), sealed.wire()));

        Envelope.Opened opened = Envelope.openRequest(server.exchange(), sealed.wire());
        byte[] forged = Envelope.sealResponse(opened.keys(), impostor.signing().getPrivate(), new byte[]{9});
        assertThrows(GeneralSecurityException.class, () -> Envelope.openResponse(sealed.keys(), server.signing().getPublic(), forged));
    }
}
