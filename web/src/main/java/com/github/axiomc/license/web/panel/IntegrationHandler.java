package com.github.axiomc.license.web.panel;

import com.github.axiomc.license.common.api.LicenseEndpoint;
import com.github.axiomc.license.common.crypto.ServerKeys;
import com.github.axiomc.license.common.http.Http;
import com.github.axiomc.license.web.auth.SecurityFilter;
import com.github.axiomc.license.web.view.Pages;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

public final class IntegrationHandler implements HttpHandler {

    public static final String PATH = "/integration";

    private final ServerKeys keys;

    public IntegrationHandler(ServerKeys keys) {
        this.keys = keys;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String proto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
        String host = exchange.getRequestHeaders().getFirst("Host");
        String base = (proto == null ? "http" : proto) + "://" + host;
        Http.html(exchange, 200, Pages.integration(SecurityFilter.account(exchange), base, LicenseEndpoint.PATH, keys.exchangePublicBase64(), keys.signingPublicBase64()));
    }
}
