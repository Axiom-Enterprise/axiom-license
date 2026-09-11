package com.github.axiomc.license.common.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class Http {

    public static final String OCTET_STREAM = "application/octet-stream";
    public static final String HTML = "text/html; charset=utf-8";

    private Http() {
    }

    public static byte[] body(HttpExchange exchange, int limit) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes(limit + 1);
            if (bytes.length > limit) {
                throw new IOException("body exceeds " + limit + " bytes");
            }
            return bytes;
        }
    }

    public static void send(HttpExchange exchange, int status, String contentType, byte[] payload) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    public static void html(HttpExchange exchange, int status, String page) throws IOException {
        send(exchange, status, HTML, page.getBytes(StandardCharsets.UTF_8));
    }

    public static void empty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    public static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        empty(exchange, 303);
    }

    public static boolean isPost(HttpExchange exchange) {
        return "POST".equals(exchange.getRequestMethod());
    }

    public static String query(HttpExchange exchange, String name) {
        URI uri = exchange.getRequestURI();
        String query = uri.getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            if (key.equals(name)) {
                return eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    public static String clientAddress(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("CF-Connecting-IP");
        return forwarded != null ? forwarded : exchange.getRemoteAddress().getAddress().getHostAddress();
    }
}
