package com.example.zalo.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Owns the Hikari connection pool and initializes the PostgreSQL schema. */
public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;

    private Database(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static Database connect(ServerConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        ServerConfig.DatabaseConfig settings = config.database();
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(settings.jdbcUrl());
        hikari.setUsername(settings.username());
        hikari.setPassword(settings.password());
        hikari.setMaximumPoolSize(settings.maxPoolSize());
        hikari.setMinimumIdle(settings.minimumIdle());
        hikari.setConnectionTimeout(settings.connectionTimeout().toMillis());
        hikari.setIdleTimeout(settings.idleTimeout().toMillis());
        hikari.setMaxLifetime(settings.maxLifetime().toMillis());
        hikari.setPoolName("zalo-server-pool");

        HikariDataSource dataSource = new HikariDataSource(hikari);
        try {
            runSchema(dataSource, settings.schemaResource());
            return new Database(dataSource);
        } catch (RuntimeException exception) {
            dataSource.close();
            throw exception;
        }
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    private static void runSchema(HikariDataSource dataSource, String resourceName) {
        List<String> statements = readStatements(resourceName);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            for (String sql : statements) {
                statement.execute(sql);
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize database schema", exception);
        }
    }

    private static List<String> readStatements(String resourceName) {
        InputStream stream = Database.class.getClassLoader().getResourceAsStream(resourceName);
        if (stream == null) {
            throw new IllegalStateException("Schema resource not found: " + resourceName);
        }

        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                current.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    statements.add(current.toString());
                    current.setLength(0);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read schema resource: " + resourceName, exception);
        }
        if (!current.toString().isBlank()) {
            throw new IllegalStateException("Schema resource ends with an incomplete statement: " + resourceName);
        }
        return statements;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
