package com.github.axiomc.license.web.panel;

import com.github.axiomc.license.common.http.Http;
import com.github.axiomc.license.web.auth.Account;
import com.github.axiomc.license.web.auth.AccountRepository;
import com.github.axiomc.license.web.auth.PasswordHasher;
import com.github.axiomc.license.web.auth.Role;
import com.github.axiomc.license.web.auth.SecurityFilter;
import com.github.axiomc.license.web.auth.SessionRegistry;
import com.github.axiomc.license.web.view.Pages;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class AccountsHandler implements HttpHandler {

    public static final String PATH = "/accounts";

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,31}");

    private final AccountRepository accounts;
    private final SessionRegistry sessions;

    public AccountsHandler(AccountRepository accounts, SessionRegistry sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Account self = SecurityFilter.account();
        if (Http.isPost(exchange)) {
            act(exchange, self, Forms.read(exchange));
            return;
        }
        List<Account> all = accounts.findAll().join();
        Http.html(exchange, 200, Pages.accounts(self, all, Http.query(exchange, "notice")));
    }

    private void act(HttpExchange exchange, Account self, Forms form) throws IOException {
        long id = form.id("id");
        CompletableFuture<String> outcome = switch (form.text("action")) {
            case "create" -> create(form);
            case "password" -> password(id, form);
            case "delete" -> id == self.id() ? CompletableFuture.completedFuture("self") : accounts.delete(id).thenRun(() -> sessions.closeAll(id)).thenApply(v -> "removed");
            default -> CompletableFuture.completedFuture("unknown");
        };
        Http.redirect(exchange, PATH + "?notice=" + outcome.join());
    }

    private CompletableFuture<String> create(Forms form) {
        String username = form.text("username").toLowerCase();
        String password = form.text("password");
        Role role = "ADMIN".equals(form.text("role")) ? Role.ADMIN : Role.MOD;
        if (!USERNAME.matcher(username).matches() || password.length() < PasswordHasher.MIN_LENGTH) {
            return CompletableFuture.completedFuture("invalid");
        }
        return accounts.findByUsername(username).thenCompose(existing -> existing.isPresent()
                ? CompletableFuture.completedFuture("taken")
                : accounts.insert(username, PasswordHasher.hash(password.toCharArray()), role, Instant.now()).thenApply(a -> "created"));
    }

    private CompletableFuture<String> password(long id, Forms form) {
        String password = form.text("password");
        if (id < 0 || password.length() < PasswordHasher.MIN_LENGTH) {
            return CompletableFuture.completedFuture("invalid");
        }
        return accounts.updatePassword(id, PasswordHasher.hash(password.toCharArray())).thenRun(() -> sessions.closeAll(id)).thenApply(v -> "reset");
    }
}
