package com.github.axiomc.license.web.panel;

import com.github.axiomc.license.common.http.Http;
import com.github.axiomc.license.web.auth.Account;
import com.github.axiomc.license.web.auth.AccountRepository;
import com.github.axiomc.license.web.auth.LoginThrottle;
import com.github.axiomc.license.web.auth.PasswordHasher;
import com.github.axiomc.license.web.auth.SecurityFilter;
import com.github.axiomc.license.web.auth.SessionRegistry;
import com.github.axiomc.license.web.view.Pages;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

public final class LoginHandler implements HttpHandler {

    public static final String LOGIN = "/login";
    public static final String LOGOUT = "/logout";

    private final AccountRepository accounts;
    private final SessionRegistry sessions;
    private final LoginThrottle throttle;

    public LoginHandler(AccountRepository accounts, SessionRegistry sessions, LoginThrottle throttle) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.throttle = throttle;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        switch (exchange.getRequestURI().getPath()) {
            case LOGOUT -> logout(exchange);
            case LOGIN -> {
                if (Http.isPost(exchange)) {
                    login(exchange);
                } else if (SecurityFilter.account(exchange) != null) {
                    Http.redirect(exchange, LicensesHandler.PATH);
                } else {
                    Http.html(exchange, 200, Pages.login(null));
                }
            }
            default -> Http.empty(exchange, 404);
        }
    }

    private void login(HttpExchange exchange) throws IOException {
        String address = Http.clientAddress(exchange);
        if (throttle.blocked(address)) {
            Http.html(exchange, 429, Pages.login("Too many attempts. Try again in fifteen minutes."));
            return;
        }
        Forms form = Forms.read(exchange);
        String username = form.text("username").toLowerCase();
        char[] password = form.text("password").toCharArray();
        Optional<AccountRepository.Credentials> found = accounts.findByUsername(username).join();
        boolean ok = PasswordHasher.verify(password, found.map(AccountRepository.Credentials::passwordHash).orElse(null));
        Arrays.fill(password, '\0');
        if (!ok) {
            throttle.failed(address);
            Http.html(exchange, 401, Pages.login("Wrong username or password."));
            return;
        }
        throttle.succeeded(address);
        Account account = found.get().account();
        String token = sessions.open(account);
        exchange.getResponseHeaders().add("Set-Cookie", cookie(exchange, token, SessionRegistry.LIFETIME_SECONDS));
        Http.redirect(exchange, LicensesHandler.PATH);
    }

    private void logout(HttpExchange exchange) throws IOException {
        if (!Http.isPost(exchange)) {
            Http.empty(exchange, 405);
            return;
        }
        sessions.close(SecurityFilter.cookie(exchange));
        exchange.getResponseHeaders().add("Set-Cookie", cookie(exchange, "", 0));
        Http.redirect(exchange, LOGIN);
    }

    private static String cookie(HttpExchange exchange, String value, long maxAge) {
        boolean secure = "https".equals(exchange.getRequestHeaders().getFirst("X-Forwarded-Proto"));
        return SessionRegistry.COOKIE + '=' + value + "; Path=/; Max-Age=" + maxAge + "; HttpOnly; SameSite=Strict" + (secure ? "; Secure" : "");
    }
}
