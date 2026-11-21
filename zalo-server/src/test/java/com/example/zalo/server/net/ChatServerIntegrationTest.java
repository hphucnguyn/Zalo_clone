package com.example.zalo.server.net;

import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.common.util.JsonUtil;
import com.example.zalo.server.config.ServerConfig;
import com.example.zalo.server.config.SslContextFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChatServerIntegrationTest {

    private static final String KEYSTORE_PASSWORD = "test-password";
    private static Path temporaryDirectory;
    private static Path keystore;
    private ChatServer server;

    @BeforeAll
    static void createTestKeystore() throws Exception {
        temporaryDirectory = Files.createTempDirectory("zalo-server-test-");
        keystore = temporaryDirectory.resolve("server.p12");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        Path keytool = Path.of(System.getProperty("java.home"), "bin", executable);
        Process process = new ProcessBuilder(
                keytool.toString(),
                "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", keystore.toString(),
                "-storepass", KEYSTORE_PASSWORD, "-keypass", KEYSTORE_PASSWORD,
                "-dname", "CN=localhost", "-validity", "1", "-noprompt")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }

    @AfterAll
    static void deleteTestKeystore() throws IOException {
        if (temporaryDirectory != null) {
            try (var paths = Files.walk(temporaryDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not delete test TLS file", exception);
                    }
                });
            }
        }
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void tlsClientReceivesPongForPing() throws Exception {
        startServer();
        try (SSLSocket socket = tlsSocket()) {
            socket.startHandshake();
            write(socket, JsonUtil.toJson(Packet.of(MessageType.PING, "ping-1", Map.of())));

            Packet response = JsonUtil.fromJson(read(socket));

            assertEquals(MessageType.PONG, response.type());
            assertEquals("ping-1", response.requestId());
        }
    }

    @Test
    void malformedJsonReceivesProtocolError() throws Exception {
        startServer();
        try (SSLSocket socket = tlsSocket()) {
            socket.startHandshake();
            write(socket, "{invalid-json}");

            Packet response = JsonUtil.fromJson(read(socket));

            assertEquals(MessageType.ERROR, response.type());
            assertEquals("INVALID_JSON", response.data().get("code").asText());
        }
    }

    @Test
    void nonTlsConnectionIsRejectedBeforeCreatingSession() throws Exception {
        startServer();
        try (Socket socket = SocketFactory.getDefault().createSocket("127.0.0.1", server.port())) {
            socket.getOutputStream().write("plain text\n".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        }

        await(() -> server.sessionRegistry().size() == 0);
        assertEquals(0, server.sessionRegistry().removalCount());
    }

    @Test
    void abruptlyDisconnectedTlsClientIsRemovedExactlyOnce() throws Exception {
        startServer();
        SSLSocket socket = tlsSocket();
        socket.startHandshake();
        await(() -> server.sessionRegistry().size() == 1);
        assertNotNull(server.sessionRegistry().snapshot().iterator().next());

        socket.close();

        await(() -> server.sessionRegistry().size() == 0);
        assertEquals(1, server.sessionRegistry().removalCount());
    }

    private void startServer() throws IOException {
        ServerConfig config = new ServerConfig(
                "127.0.0.1",
                0,
                10,
                4,
                Duration.ofSeconds(3),
                new ServerConfig.TlsConfig(keystore, "PKCS12", KEYSTORE_PASSWORD, new String[]{"TLSv1.3", "TLSv1.2"}, false),
                new ServerConfig.ProtocolConfig(65_536, StandardCharsets.UTF_8, 32_768),
                new ServerConfig.DatabaseConfig("jdbc:unused", "unused", "unused", "schema.sql", 1, 0,
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1)));
        server = new ChatServer(config, SslContextFactory.create(config.tls()));
        server.start();
    }

    private SSLSocket tlsSocket() throws IOException, GeneralSecurityException {
        return (SSLSocket) trustAllClientContext().getSocketFactory().createSocket("127.0.0.1", server.port());
    }

    private static SSLContext trustAllClientContext() throws GeneralSecurityException {
        TrustManager[] trustManagers = {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers, new SecureRandom());
        return context;
    }

    private static void write(SSLSocket socket, String line) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    private static String read(SSLSocket socket) throws IOException {
        socket.setSoTimeout(3_000);
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
    }

    private static void await(Condition condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.matches()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for expected server state");
            }
            Thread.sleep(20);
        }
    }

    @FunctionalInterface
    private interface Condition {
        boolean matches();
    }
}
