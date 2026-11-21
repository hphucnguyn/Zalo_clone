package com.example.zalo.server.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerConfigTest {

    @Test
    void loadsPropertiesAndRequiredSecretsFromEnvironment() {
        Properties properties = properties();

        ServerConfig config = ServerConfig.from(properties, Map.of(
                "KEYSTORE_PASSWORD", "keystore-secret",
                "DB_USER", "zalo_app",
                "DB_PASSWORD", "database-secret"));

        assertEquals(5000, config.port());
        assertEquals(65_536, config.protocol().maxLineBytes());
        assertEquals("zalo_app", config.database().username());
    }

    @Test
    void rejectsMissingRequiredEnvironmentVariableWithoutExposingValue() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ServerConfig.from(properties(), Map.of()));

        assertEquals("Required environment variable is not set: KEYSTORE_PASSWORD", exception.getMessage());
    }

    private static Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("server.host", "0.0.0.0");
        properties.setProperty("server.port", "5000");
        properties.setProperty("server.backlog", "100");
        properties.setProperty("server.maxClients", "500");
        properties.setProperty("server.readTimeoutSeconds", "120");
        properties.setProperty("tls.keystore.path", "certificates/server.p12");
        properties.setProperty("tls.keystore.type", "PKCS12");
        properties.setProperty("tls.keystore.passwordEnv", "KEYSTORE_PASSWORD");
        properties.setProperty("tls.protocols", "TLSv1.3,TLSv1.2");
        properties.setProperty("tls.needClientAuth", "false");
        properties.setProperty("protocol.maxLineBytes", "65536");
        properties.setProperty("protocol.charset", "UTF-8");
        properties.setProperty("protocol.fileChunkBytes", "32768");
        properties.setProperty("auth.bcrypt.rounds", "12");
        properties.setProperty("auth.maxLoginAttempts", "5");
        properties.setProperty("auth.lockSeconds", "60");
        properties.setProperty("db.url", "jdbc:postgresql://localhost:5433/zalo");
        properties.setProperty("db.user.env", "DB_USER");
        properties.setProperty("db.password.env", "DB_PASSWORD");
        properties.setProperty("db.schema", "schema.sql");
        properties.setProperty("db.pool.maxSize", "10");
        properties.setProperty("db.pool.minIdle", "2");
        properties.setProperty("db.pool.connectionTimeoutMs", "3000");
        properties.setProperty("db.pool.idleTimeoutMs", "300000");
        properties.setProperty("db.pool.maxLifetimeMs", "1800000");
        return properties;
    }
}
