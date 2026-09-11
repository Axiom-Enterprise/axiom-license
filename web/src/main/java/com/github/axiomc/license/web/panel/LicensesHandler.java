package com.github.axiomc.license.web.panel;

import com.github.axiomc.license.common.api.LicenseKeys;
import com.github.axiomc.license.common.domain.License;
import com.github.axiomc.license.common.domain.LicenseStatus;
import com.github.axiomc.license.common.http.Http;
import com.github.axiomc.license.common.persistence.LicenseRepository;
import com.github.axiomc.license.web.auth.Role;
import com.github.axiomc.license.web.auth.SecurityFilter;
import com.github.axiomc.license.web.view.Pages;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class LicensesHandler implements HttpHandler {

    public static final String PATH = "/licenses";

    private static final int MAX_FIELD = 64;
    private static final int MAX_YEAR = 9999;

    private final LicenseRepository licenses;

    public LicensesHandler(LicenseRepository licenses) {
        this.licenses = licenses;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (Http.isPost(exchange)) {
            act(exchange, SecurityFilter.account().role() == Role.ADMIN, Forms.read(exchange));
            return;
        }
        List<License> all = licenses.findAll().join();
        Http.html(exchange, 200, Pages.licenses(SecurityFilter.account(), all, Http.query(exchange, "notice"), Http.query(exchange, "issued")));
    }

    private void act(HttpExchange exchange, boolean admin, Forms form) throws IOException {
        long id = form.id("id");
        CompletableFuture<String> outcome = switch (form.text("action")) {
            case "issue" -> issue(form);
            case "revoke" -> licenses.setStatus(id, LicenseStatus.REVOKED).thenApply(v -> "revoked");
            case "restore" -> licenses.setStatus(id, LicenseStatus.ACTIVE).thenApply(v -> "restored");
            case "unbind" -> licenses.unbindHardware(id).thenApply(v -> "unbound");
            case "delete" -> admin ? licenses.delete(id).thenApply(v -> "deleted") : CompletableFuture.completedFuture("forbidden");
            default -> CompletableFuture.completedFuture("unknown");
        };
        Http.redirect(exchange, PATH + "?notice=" + outcome.join());
    }

    private CompletableFuture<String> issue(Forms form) {
        String product = form.text("product");
        String holder = form.text("holder");
        if (product.isEmpty() || holder.isEmpty() || product.length() > MAX_FIELD || holder.length() > MAX_FIELD) {
            return CompletableFuture.completedFuture("invalid");
        }
        Instant expiresAt;
        try {
            String expires = form.text("expires");
            LocalDate date = expires.isEmpty() ? null : LocalDate.parse(expires);
            if (date != null && date.getYear() > MAX_YEAR) {
                return CompletableFuture.completedFuture("invalid");
            }
            expiresAt = date == null ? null : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return CompletableFuture.completedFuture("invalid");
        }
        return licenses.insert(LicenseKeys.next(), product, holder, expiresAt, Instant.now()).thenApply(created -> "issued&issued=" + created.id());
    }
}
