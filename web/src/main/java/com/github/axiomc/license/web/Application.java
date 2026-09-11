package com.github.axiomc.license.web;

import com.github.axiomc.license.common.api.LicenseEndpoint;
import com.github.axiomc.license.common.api.LicenseService;
import com.github.axiomc.license.common.crypto.ServerKeys;
import com.github.axiomc.license.common.http.Http;
import com.github.axiomc.license.common.persistence.Database;
import com.github.axiomc.license.common.persistence.LicenseRepository;
import com.github.axiomc.license.web.auth.AccountRepository;
import com.github.axiomc.license.web.auth.LoginThrottle;
import com.github.axiomc.license.web.auth.PasswordHasher;
import com.github.axiomc.license.web.auth.Role;
import com.github.axiomc.license.web.auth.SecurityFilter;
import com.github.axiomc.license.web.auth.SessionRegistry;
import com.github.axiomc.license.web.panel.AccountsHandler;
import com.github.axiomc.license.web.panel.AssetHandler;
import com.github.axiomc.license.web.panel.IntegrationHandler;
import com.github.axiomc.license.web.panel.LicensesHandler;
import com.github.axiomc.license.web.panel.LoginHandler;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.Executors;

public final class Application {

    private static final System.Logger LOG = System.getLogger(Application.class.getName());

    private Application() {
    }

    public static void main(String[] args) throws Exception {
        String bind = env("AXIOM_BIND", "127.0.0.1");
        int port = Integer.parseInt(env("AXIOM_PORT", "8080"));
        Path data = Path.of(env("AXIOM_DATA", "data"));

        Database database = Database.open(data.resolve("axiom.db"));
        ServerKeys keys = ServerKeys.loadOrCreate(data.resolve("keys"));
        LicenseRepository licenses = new LicenseRepository(database);
        AccountRepository accounts = new AccountRepository(database);
        SessionRegistry sessions = new SessionRegistry();
        LoginThrottle throttle = new LoginThrottle();
        bootstrapAdmin(accounts);

        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 256);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        LoginHandler login = new LoginHandler(accounts, sessions, throttle);
        server.createContext(LicenseEndpoint.PATH, new LicenseEndpoint(new LicenseService(licenses, Clock.systemUTC()), keys));
        server.createContext(AssetHandler.PATH, new AssetHandler());
        route(server, LoginHandler.LOGIN, login, sessions, null);
        route(server, LoginHandler.LOGOUT, login, sessions, null);
        route(server, LicensesHandler.PATH, new LicensesHandler(licenses), sessions, Role.MOD);
        route(server, IntegrationHandler.PATH, new IntegrationHandler(keys), sessions, Role.MOD);
        route(server, AccountsHandler.PATH, new AccountsHandler(accounts, sessions), sessions, Role.ADMIN);
        route(server, "/", exchange -> Http.redirect(exchange, "/".equals(exchange.getRequestURI().getPath()) ? LicensesHandler.PATH : "/login"), sessions, null);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            database.close();
        }));
        server.start();
        LOG.log(System.Logger.Level.INFO, "Axiom listening on http://{0}:{1,number,#}", bind, port);
        LOG.log(System.Logger.Level.INFO, "exchange public key {0}", keys.exchangePublicBase64());
        LOG.log(System.Logger.Level.INFO, "signing public key  {0}", keys.signingPublicBase64());
    }

    private static void route(HttpServer server, String path, HttpHandler handler, SessionRegistry sessions, Role required) {
        HttpContext context = server.createContext(path, handler);
        context.getFilters().add(new SecurityFilter(sessions, required));
    }

    private static void bootstrapAdmin(AccountRepository accounts) {
        if (accounts.count().join() > 0) {
            return;
        }
        byte[] secret = new byte[18];
        new SecureRandom().nextBytes(secret);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        accounts.insert("admin", PasswordHasher.hash(password.toCharArray()), Role.ADMIN, Instant.now()).join();
        LOG.log(System.Logger.Level.WARNING, "first start: account admin created with password {0}; change it after signing in", password);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
