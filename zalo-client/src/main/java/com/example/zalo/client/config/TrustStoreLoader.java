package com.example.zalo.client.config;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

/** Builds an SSL context that trusts only the configured server public certificate. */
public final class TrustStoreLoader {

    private TrustStoreLoader() {
    }

    public static SSLContext load(ClientConfig config) {
        try (InputStream input = Files.newInputStream(config.trustCertificatePath())) {
            Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("zalo-server", certificate);
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException | java.io.IOException exception) {
            throw new IllegalStateException(
                    "Could not load trusted server certificate '" + config.trustCertificatePath() + "'", exception);
        }
    }
}
