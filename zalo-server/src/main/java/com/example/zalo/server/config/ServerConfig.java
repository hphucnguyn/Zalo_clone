package com.example.zalo.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Immutable server settings loaded from {@code server.properties} and environment variables. */
public record ServerConfig(
        String host,
        int port,
        int backlog,
        int maxClients,
        Duration readTimeout,
        TlsConfig tls,
        ProtocolConfig protocol,
        DatabaseConfig database) {

    private static final String RESOURCE_NAME = "server.properties";

    public static ServerConfig load() {
        try (InputStream input = ServerConfig.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (input == null) {
                throw new IllegalStateException("Missing classpath resource: " + RESOURCE_NAME);
            }
            Properties properties = new Properties();
            properties.load(input);
            return from(properties, System.getenv());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load " + RESOURCE_NAME, exception);
        }
    }

    static ServerConfig from(Properties properties, Map<String, String> environment) {
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(environment, "environment must not be null");

        String keystorePasswordEnv = required(properties, "tls.keystore.passwordEnv");
        String databaseUserEnv = required(properties, "db.user.env");
        String databasePasswordEnv = required(properties, "db.password.env");

        return new ServerConfig(
                required(properties, "server.host"),
                positiveInt(properties, "server.port"),
                positiveInt(properties, "server.backlog"),
                positiveInt(properties, "server.maxClients"),
                Duration.ofSeconds(positiveInt(properties, "server.readTimeoutSeconds")),
                new TlsConfig(
                        Path.of(required(properties, "tls.keystore.path")),
                        required(properties, "tls.keystore.type"),
                        requiredEnvironment(environment, keystorePasswordEnv),
                        required(properties, "tls.protocols").split(","),
                        Boolean.parseBoolean(required(properties, "tls.needClientAuth"))),
                new ProtocolConfig(
                        positiveInt(properties, "protocol.maxLineBytes"),
                        Charset.forName(required(properties, "protocol.charset")),
                        positiveInt(properties, "protocol.fileChunkBytes")),
                new DatabaseConfig(
                        required(properties, "db.url"),
                        requiredEnvironment(environment, databaseUserEnv),
                        requiredEnvironment(environment, databasePasswordEnv),
                        required(properties, "db.schema"),
                        positiveInt(properties, "db.pool.maxSize"),
                        nonNegativeInt(properties, "db.pool.minIdle"),
                        Duration.ofMillis(positiveInt(properties, "db.pool.connectionTimeoutMs")),
                        Duration.ofMillis(positiveInt(properties, "db.pool.idleTimeoutMs")),
                        Duration.ofMillis(positiveInt(properties, "db.pool.maxLifetimeMs"))));
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value.trim();
    }

    private static String requiredEnvironment(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable is not set: " + name);
        }
        return value;
    }

    private static int positiveInt(Properties properties, String key) {
        int value = integer(properties, key);
        if (value <= 0) {
            throw new IllegalArgumentException("Property must be positive: " + key);
        }
        return value;
    }

    private static int nonNegativeInt(Properties properties, String key) {
        int value = integer(properties, key);
        if (value < 0) {
            throw new IllegalArgumentException("Property must not be negative: " + key);
        }
        return value;
    }

    private static int integer(Properties properties, String key) {
        try {
            return Integer.parseInt(required(properties, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Property must be an integer: " + key, exception);
        }
    }

    public record TlsConfig(
            Path keystorePath,
            String keystoreType,
            String keystorePassword,
            String[] protocols,
            boolean needClientAuth) {
    }

    public record ProtocolConfig(int maxLineBytes, Charset charset, int fileChunkBytes) {
    }

    public record DatabaseConfig(
            String jdbcUrl,
            String username,
            String password,
            String schemaResource,
            int maxPoolSize,
            int minimumIdle,
            Duration connectionTimeout,
            Duration idleTimeout,
            Duration maxLifetime) {
    }
}
