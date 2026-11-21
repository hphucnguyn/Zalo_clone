package com.example.zalo.server.config;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;

/** Creates the TLS context from the server's private PKCS12 keystore. */
public final class SslContextFactory {

    private SslContextFactory() {
    }

    public static SSLContext create(ServerConfig.TlsConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        Path keystorePath = locateKeystore(config.keystorePath());
        char[] password = config.keystorePassword().toCharArray();
        try (InputStream input = Files.newInputStream(keystorePath)) {
            KeyStore keyStore = KeyStore.getInstance(config.keystoreType());
            keyStore.load(input, password);
            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, password);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers.getKeyManagers(), null, null);
            return context;
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Could not load TLS keystore '" + keystorePath
                            + "'. Check the file, its type, and the configured password environment variable.",
                    exception);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static Path locateKeystore(Path configuredPath) {
        if (Files.isRegularFile(configuredPath)) {
            return configuredPath;
        }
        Path moduleRelativePath = Path.of("zalo-server").resolve(configuredPath);
        if (Files.isRegularFile(moduleRelativePath)) {
            return moduleRelativePath;
        }
        throw new IllegalStateException(
                "TLS keystore not found at '" + configuredPath + "' (or '" + moduleRelativePath + "').");
    }
}
