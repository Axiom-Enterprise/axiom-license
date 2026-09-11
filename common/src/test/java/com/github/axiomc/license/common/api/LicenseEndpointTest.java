package com.github.axiomc.license.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.axiomc.license.common.client.LicenseClient;
import com.github.axiomc.license.common.crypto.ServerKeys;
import com.github.axiomc.license.common.domain.License;
import com.github.axiomc.license.common.persistence.Database;
import com.github.axiomc.license.common.persistence.LicenseRepository;
import com.github.axiomc.license.common.protocol.LicenseResponse;
import com.github.axiomc.license.common.protocol.LicenseVerdict;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseEndpointTest {

    @TempDir
    Path dir;

    private Database database;
    private LicenseRepository licenses;
    private HttpServer server;
    private URI base;
    private ServerKeys keys;

    @BeforeEach
    void start() throws Exception {
        database = Database.open(dir.resolve("api.db"));
        licenses = new LicenseRepository(database);
        keys = ServerKeys.loadOrCreate(dir.resolve("keys"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext(LicenseEndpoint.PATH, new LicenseEndpoint(new LicenseService(licenses, Clock.systemUTC()), keys));
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
        database.close();
    }

    @Test
    void clientAndServerAgreeThroughTheEnvelope() throws Exception {
        License issued = licenses.insert(LicenseKeys.next(), "app", "carlo", null, Instant.now()).join();
        LicenseClient client = new LicenseClient(base, keys.exchangePublicBase64(), keys.signingPublicBase64());
        LicenseResponse first = client.verify(issued.key().toLowerCase(), "app", "machine-a").join();
        assertEquals(LicenseVerdict.VALID, first.verdict());
        assertEquals(LicenseVerdict.HARDWARE_MISMATCH, client.verify(issued.key(), "app", "machine-b").join().verdict());
        assertEquals(LicenseVerdict.UNKNOWN_KEY, client.verify("AXM-00000-00000-00000-00000", "app", "machine-a").join().verdict());
    }

    @Test
    void wrongServerKeysAndGarbageAreRejected() throws Exception {
        ServerKeys other = ServerKeys.generate();
        LicenseClient client = new LicenseClient(base, other.exchangePublicBase64(), other.signingPublicBase64());
        assertThrows(CompletionException.class, () -> client.verify("AXM-1", "app", "hw").join());

        HttpRequest garbage = HttpRequest.newBuilder(base.resolve(LicenseEndpoint.PATH)).POST(HttpRequest.BodyPublishers.ofByteArray(new byte[300])).build();
        HttpResponse<Void> response = HttpClient.newHttpClient().send(garbage, HttpResponse.BodyHandlers.discarding());
        assertEquals(400, response.statusCode());
        HttpResponse<Void> get = HttpClient.newHttpClient().send(HttpRequest.newBuilder(base.resolve(LicenseEndpoint.PATH)).GET().build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(405, get.statusCode());
    }
}
