package com.github.axiomc.license.web.panel;

import com.github.axiomc.license.common.http.Http;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AssetHandler implements HttpHandler {

    public static final String PATH = "/assets/app.css";

    private static final byte[] STYLESHEET = load();

    public static final String HREF = PATH + "?v=" + digest(STYLESHEET);

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod()) || !PATH.equals(exchange.getRequestURI().getPath())) {
            Http.empty(exchange, 404);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/css; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
        exchange.sendResponseHeaders(200, STYLESHEET.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(STYLESHEET);
        }
    }

    private static byte[] load() {
        try (InputStream in = AssetHandler.class.getResourceAsStream("/web/app.css")) {
            if (in == null) {
                throw new IllegalStateException("stylesheet missing from classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
