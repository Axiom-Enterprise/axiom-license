package com.github.axiomc.license.common.persistence;

import com.github.axiomc.license.common.domain.License;
import com.github.axiomc.license.common.domain.LicenseStatus;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class LicenseRepository {

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS licenses (
                id           INTEGER PRIMARY KEY,
                key          TEXT    NOT NULL UNIQUE,
                product      TEXT    NOT NULL,
                holder       TEXT    NOT NULL,
                hardware_id  TEXT,
                expires_at   INTEGER,
                status       TEXT    NOT NULL,
                created_at   INTEGER NOT NULL,
                last_seen_at INTEGER
            )
            """;
    private static final String COLUMNS = "id, key, product, holder, hardware_id, expires_at, status, created_at, last_seen_at";

    private final Database database;

    public LicenseRepository(Database database) {
        this.database = database;
        database.migrate(SCHEMA);
    }

    public CompletableFuture<Optional<License>> findByKey(String key) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM licenses WHERE key = ?")) {
                statement.setString(1, key);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<List<License>> findAll() {
        return database.read(connection -> {
            List<License> licenses = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM licenses ORDER BY id DESC");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    licenses.add(map(rows));
                }
            }
            return licenses;
        });
    }

    public CompletableFuture<License> insert(String key, String product, String holder, Instant expiresAt, Instant now) {
        return database.write(connection -> {
            String sql = "INSERT INTO licenses (key, product, holder, expires_at, status, created_at) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, key);
                statement.setString(2, product);
                statement.setString(3, holder);
                setInstant(statement, 4, expiresAt);
                statement.setString(5, LicenseStatus.ACTIVE.name());
                statement.setLong(6, now.toEpochMilli());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    return new License(keys.getLong(1), key, product, holder, null, expiresAt, LicenseStatus.ACTIVE, now, null);
                }
            }
        });
    }

    public CompletableFuture<Boolean> bindHardware(long id, String hardwareId, Instant now) {
        return database.write(connection -> {
            String sql = "UPDATE licenses SET hardware_id = ?, last_seen_at = ? WHERE id = ? AND (hardware_id IS NULL OR hardware_id = ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, hardwareId);
                statement.setLong(2, now.toEpochMilli());
                statement.setLong(3, id);
                statement.setString(4, hardwareId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Void> touch(long id, Instant now) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE licenses SET last_seen_at = ? WHERE id = ?")) {
                statement.setLong(1, now.toEpochMilli());
                statement.setLong(2, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> unbindHardware(long id) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE licenses SET hardware_id = NULL WHERE id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> setStatus(long id, LicenseStatus status) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE licenses SET status = ? WHERE id = ?")) {
                statement.setString(1, status.name());
                statement.setLong(2, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> delete(long id) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM licenses WHERE id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static License map(ResultSet rows) throws SQLException {
        return new License(
                rows.getLong("id"),
                rows.getString("key"),
                rows.getString("product"),
                rows.getString("holder"),
                rows.getString("hardware_id"),
                instant(rows, "expires_at"),
                LicenseStatus.valueOf(rows.getString("status")),
                Instant.ofEpochMilli(rows.getLong("created_at")),
                instant(rows, "last_seen_at"));
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        long millis = rows.getLong(column);
        return rows.wasNull() ? null : Instant.ofEpochMilli(millis);
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value.toEpochMilli());
        }
    }
}
