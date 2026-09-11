package com.github.axiomc.license.web.auth;

import com.github.axiomc.license.common.persistence.Database;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class AccountRepository {

    public record Credentials(Account account, String passwordHash) {
    }

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS accounts (
                id            INTEGER PRIMARY KEY,
                username      TEXT    NOT NULL UNIQUE,
                password_hash TEXT    NOT NULL,
                role          TEXT    NOT NULL,
                created_at    INTEGER NOT NULL
            )
            """;

    private final Database database;

    public AccountRepository(Database database) {
        this.database = database;
        database.migrate(SCHEMA);
    }

    public CompletableFuture<Optional<Credentials>> findByUsername(String username) {
        return database.read(connection -> {
            String sql = "SELECT id, username, password_hash, role, created_at FROM accounts WHERE username = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(new Credentials(map(rows), rows.getString("password_hash"))) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<List<Account>> findAll() {
        return database.read(connection -> {
            List<Account> accounts = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT id, username, role, created_at FROM accounts ORDER BY username");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    accounts.add(map(rows));
                }
            }
            return accounts;
        });
    }

    public CompletableFuture<Long> count() {
        return database.read(connection -> {
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM accounts")) {
                rows.next();
                return rows.getLong(1);
            }
        });
    }

    public CompletableFuture<Account> insert(String username, String passwordHash, Role role, Instant now) {
        return database.write(connection -> {
            String sql = "INSERT INTO accounts (username, password_hash, role, created_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, username);
                statement.setString(2, passwordHash);
                statement.setString(3, role.name());
                statement.setLong(4, now.toEpochMilli());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    return new Account(keys.getLong(1), username, role, now);
                }
            }
        });
    }

    public CompletableFuture<Void> updatePassword(long id, String passwordHash) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE accounts SET password_hash = ? WHERE id = ?")) {
                statement.setString(1, passwordHash);
                statement.setLong(2, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> delete(long id) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static Account map(ResultSet rows) throws SQLException {
        return new Account(rows.getLong("id"), rows.getString("username"), Role.valueOf(rows.getString("role")), Instant.ofEpochMilli(rows.getLong("created_at")));
    }
}
