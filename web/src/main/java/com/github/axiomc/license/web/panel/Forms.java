package com.github.axiomc.license.web.panel;

import com.github.axiomc.license.common.http.Http;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class Forms {

    private static final int MAX_BODY = 8 * 1024;

    private final Map<String, String> fields;

    private Forms(Map<String, String> fields) {
        this.fields = fields;
    }

    public static Forms read(HttpExchange exchange) throws IOException {
        String body = new String(Http.body(exchange, MAX_BODY), StandardCharsets.UTF_8);
        Map<String, String> fields = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            fields.putIfAbsent(name, value);
        }
        return new Forms(fields);
    }

    public String text(String name) {
        return fields.getOrDefault(name, "").strip();
    }

    public long id(String name) {
        try {
            return Long.parseLong(text(name));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
