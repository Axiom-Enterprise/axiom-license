package com.github.axiomc.license.web.auth;

import com.github.axiomc.license.common.http.Http;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public final class SecurityFilter extends Filter {

    public static final String ACCOUNT = "account";

    private static final String CSP = "default-src 'none'; style-src 'self'; img-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'";

    private final SessionRegistry sessions;
    private final Role required;

    public SecurityFilter(SessionRegistry sessions, Role required) {
        this.sessions = sessions;
        this.required = required;
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        harden(exchange.getResponseHeaders());
        if (Http.isPost(exchange) && crossSite(exchange.getRequestHeaders())) {
            Http.empty(exchange, 403);
            return;
        }
        Optional<Account> account = sessions.resolve(cookie(exchange));
        if (account.isPresent()) {
            exchange.setAttribute(ACCOUNT, account.get());
        }
        if (required != null) {
            if (account.isEmpty()) {
                Http.redirect(exchange, "/login");
                return;
            }
            if (!account.get().role().covers(required)) {
                Http.empty(exchange, 403);
                return;
            }
        }
        chain.doFilter(exchange);
    }

    @Override
    public String description() {
        return required == null ? "public" : "requires " + required;
    }

    public static Account account(HttpExchange exchange) {
        return (Account) exchange.getAttribute(ACCOUNT);
    }

    public static String cookie(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String header : cookies) {
            for (String pair : header.split(";")) {
                String trimmed = pair.strip();
                if (trimmed.startsWith(SessionRegistry.COOKIE + '=')) {
                    return trimmed.substring(SessionRegistry.COOKIE.length() + 1);
                }
            }
        }
        return null;
    }

    private static void harden(Headers headers) {
        headers.set("Content-Security-Policy", CSP);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        headers.set("Cross-Origin-Opener-Policy", "same-origin");
    }

    private static boolean crossSite(Headers headers) {
        String site = headers.getFirst("Sec-Fetch-Site");
        return !("same-origin".equals(site) || "none".equals(site));
    }
}
