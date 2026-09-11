package com.github.axiomc.license.common.client;

import com.github.axiomc.license.common.crypto.Envelope;
import com.github.axiomc.license.common.crypto.ServerKeys;
import com.github.axiomc.license.common.protocol.LicenseRequest;
import com.github.axiomc.license.common.protocol.LicenseResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class LicenseClient {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final HttpClient http;
    private final URI endpoint;
    private final PublicKey exchange;
    private final PublicKey signing;

    public LicenseClient(URI server, String exchangePublicBase64, String signingPublicBase64) throws GeneralSecurityException {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.endpoint = server.resolve("/v1/license/verify");
        this.exchange = ServerKeys.decodePublic(exchangePublicBase64, ServerKeys.EXCHANGE_ALGORITHM);
        this.signing = ServerKeys.decodePublic(signingPublicBase64, ServerKeys.SIGNING_ALGORITHM);
    }

    public CompletableFuture<LicenseResponse> verify(String key, String product, String hardwareId) {
        LicenseRequest request = new LicenseRequest(key, product, hardwareId, System.currentTimeMillis(), RANDOM.nextLong());
        Envelope.Sealed sealed;
        try {
            sealed = Envelope.sealRequest(exchange, request.encode());
        } catch (GeneralSecurityException e) {
            return CompletableFuture.failedFuture(e);
        }
        HttpRequest call = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(sealed.wire()))
                .build();
        return http.sendAsync(call, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> open(response, sealed, request));
    }

    private LicenseResponse open(HttpResponse<byte[]> response, Envelope.Sealed sealed, LicenseRequest request) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException("licence server answered " + response.statusCode());
        }
        try {
            LicenseResponse verdict = LicenseResponse.decode(Envelope.openResponse(sealed.keys(), signing, response.body()));
            if (verdict.nonce() != request.nonce()) {
                throw new IllegalStateException("response does not match request");
            }
            return verdict;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("response could not be authenticated", e);
        }
    }
}
