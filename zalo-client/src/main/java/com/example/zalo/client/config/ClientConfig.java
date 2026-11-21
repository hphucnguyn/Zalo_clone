package com.example.zalo.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** Immutable client settings loaded from {@code client.properties}. */
public record ClientConfig(
        String serverHost,
        int serverPort,
        int connectTimeoutMillis,
        Path trustCertificatePath,
        String[] tlsProtocols,
        String expectedHost,
        boolean cacheEnabled,
        Path cacheDirectory,
        int cacheMaxMessagesPerConversation,
        String windowTitle) {

    public static ClientConfig load() {
        try (InputStream input = ClientConfig.class.getClassLoader().getResourceAsStream("client.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing classpath resource: client.properties");
            }
            Properties properties = new Properties();
            properties.load(input);
            return from(properties);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load client.properties", exception);
        }
    }

    static ClientConfig from(Properties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        return new ClientConfig(
                required(properties, "server.host"),
                positiveInt(properties, "server.port"),
                positiveInt(properties, "connect.timeoutMillis"),
                locateCertificate(Path.of(required(properties, "tls.truststore.path"))),
                required(properties, "tls.protocols").split(","),
                required(properties, "tls.expectedHost"),
                Boolean.parseBoolean(required(properties, "cache.enabled")),
                Path.of(required(properties, "cache.dir")),
                positiveInt(properties, "cache.maxMessagesPerConversation"),
                required(properties, "ui.title"));
    }

    private static Path locateCertificate(Path configuredPath) {
        if (Files.isRegularFile(configuredPath)) {
            return configuredPath;
        }
        Path moduleRelative = Path.of("zalo-client").resolve(configuredPath);
        return Files.isRegularFile(moduleRelative) ? moduleRelative : configuredPath;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value.trim();
    }

    private static int positiveInt(Properties properties, String key) {
        try {
            int value = Integer.parseInt(required(properties, key));
            if (value <= 0) {
                throw new IllegalArgumentException("Property must be positive: " + key);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Property must be an integer: " + key, exception);
        }
    }
}
