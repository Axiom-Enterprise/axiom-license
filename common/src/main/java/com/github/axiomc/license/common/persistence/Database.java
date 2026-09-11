package com.github.axiomc.license.common.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class Database implements AutoCloseable {

    private final String url;
    private final ExecutorService writer;
    private final ExecutorService readers;
    private final ThreadLocal<Connection> connections = new ThreadLocal<>();
    private final List<Connection> open = new ArrayList<>();

    private Database(String url, int readerThreads) {
        this.url = url;
        this.writer = Executors.newSingleThreadExecutor(named("db-writer"));
        this.readers = Executors.newFixedThreadPool(readerThreads, named("db-reader"));
    }

    public static Database open(Path file) throws SQLException {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new SQLException("cannot create database directory", e);
        }
        Database database = new Database("jdbc:sqlite:" + file.toAbsolutePath(), Math.max(4, Runtime.getRuntime().availableProcessors()));
        database.write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
            }
            return null;
        }).join();
        return database;
    }

    public <T> CompletableFuture<T> read(SqlWork<T> work) {
        return CompletableFuture.supplyAsync(() -> execute(work), readers);
    }

    public <T> CompletableFuture<T> write(SqlWork<T> work) {
        return CompletableFuture.supplyAsync(() -> execute(work), writer);
    }

    public void migrate(String ddl) {
        write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(ddl);
            }
            return null;
        }).join();
    }

    private <T> T execute(SqlWork<T> work) {
        try {
            return work.run(connection());
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = connections.get();
        if (connection == null) {
            connection = DriverManager.getConnection(url);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
            }
            connections.set(connection);
            synchronized (open) {
                open.add(connection);
            }
        }
        return connection;
    }

    @Override
    public void close() {
        writer.shutdown();
        readers.shutdown();
        synchronized (open) {
            for (Connection connection : open) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
            open.clear();
        }
    }

    private static ThreadFactory named(String prefix) {
        return Thread.ofPlatform().name(prefix + "-", 0).daemon(true).factory();
    }
}
