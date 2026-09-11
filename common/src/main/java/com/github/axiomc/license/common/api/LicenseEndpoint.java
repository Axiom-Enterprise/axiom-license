package com.github.axiomc.license.common.api;

import com.github.axiomc.license.common.crypto.Envelope;
import com.github.axiomc.license.common.crypto.ServerKeys;
import com.github.axiomc.license.common.http.Http;
import com.github.axiomc.license.common.http.RateLimiter;
import com.github.axiomc.license.common.protocol.LicenseRequest;
import com.github.axiomc.license.common.protocol.LicenseResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.security.GeneralSecurityException;

public final class LicenseEndpoint implements HttpHandler {

    public static final String PATH = "/v1/license/verify";

    private static final int MAX_BODY = 1024;
    private static final RateLimiter LIMITER = new RateLimiter(60, 60_000);
    private static final System.Logger LOG = System.getLogger(LicenseEndpoint.class.getName());

    private final LicenseService service;
    private final ServerKeys keys;

    public LicenseEndpoint(LicenseService service, ServerKeys keys) {
        this.service = service;
        this.keys = keys;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!Http.isPost(exchange)) {
            Http.empty(exchange, 405);
            return;
        }
        if (!LIMITER.allow(Http.clientAddress(exchange))) {
            Http.empty(exchange, 429);
            return;
        }
        try {
            Envelope.Opened opened = Envelope.openRequest(keys.exchange(), Http.body(exchange, MAX_BODY));
            LicenseRequest request = LicenseRequest.decode(opened.plaintext());
            LicenseResponse response = service.verify(request).join();
            byte[] wire = Envelope.sealResponse(opened.keys(), keys.signing().getPrivate(), response.encode());
            Http.send(exchange, 200, Http.OCTET_STREAM, wire);
        } catch (GeneralSecurityException | RuntimeException | IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "rejected verify from {0}: {1}", Http.clientAddress(exchange), e.getMessage());
            Http.empty(exchange, 400);
        }
    }
}
