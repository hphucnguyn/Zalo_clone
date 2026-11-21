package com.example.zalo.client.config;

import com.example.zalo.client.net.TcpClient;
import com.example.zalo.client.service.AuthClient;
import com.example.zalo.client.service.AuthResponse;
import com.example.zalo.client.state.ConnectionState;
import com.example.zalo.server.config.ServerConfig;
import com.example.zalo.server.config.SslContextFactory;
import com.example.zalo.server.net.ChatServer;
import com.example.zalo.server.repository.UserRepository;
import com.example.zalo.server.security.LoginAttemptLimiter;
import com.example.zalo.server.security.PasswordHasher;
import com.example.zalo.server.service.AuthService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ClientServerIntegrationTest {

    private static final String KEYSTORE_PASSWORD = "test-password";
    private static Path temporaryDirectory;
    private static Path keystore;
    private static Path certificate;
    private ChatServer server;
    private TcpClient client;

    @BeforeAll
    static void createTlsMaterial() throws Exception {
        temporaryDirectory = Files.createTempDirectory("zalo-client-test-");
        keystore = temporaryDirectory.resolve("server.p12");
        certificate = temporaryDirectory.resolve("server.crt");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        Path keytool = Path.of(System.getProperty("java.home"), "bin", executable);
        runKeytool(keytool,
                "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", keystore.toString(),
                "-storepass", KEYSTORE_PASSWORD, "-keypass", KEYSTORE_PASSWORD,
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-validity", "1", "-noprompt");
        runKeytool(keytool,
                "-exportcert", "-rfc", "-alias", "server", "-storetype", "PKCS12",
                "-keystore", keystore.toString(), "-storepass", KEYSTORE_PASSWORD,
                "-file", certificate.toString());
    }

    @AfterAll
    static void deleteTlsMaterial() throws IOException {
        if (temporaryDirectory != null) {
            try (var paths = Files.walk(temporaryDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not delete temporary TLS material", exception);
                    }
                });
            }
        }
    }

    @AfterEach
    void closeConnections() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void clientRegistersAndLogsInAgainstTlsServer() throws Exception {
        startServer();
        AuthClient authClient = connectClient("localhost");

        AuthResponse registration = authClient.register("client_user", "correct-password", "Client User")
                .get(3, TimeUnit.SECONDS);
        AuthResponse login = authClient.login("client_user", "correct-password").get(3, TimeUnit.SECONDS);

        assertTrue(registration.successful());
        assertTrue(login.successful());
        assertEquals(ConnectionState.AUTHENTICATED, client.state());
    }

    @Test
    void clientRejectsCertificateForUnexpectedHost() throws Exception {
        startServer();
        ClientConfig config = clientConfig("wrong.example");
        client = new TcpClient(config, TrustStoreLoader.load(config));

        assertThrows(IOException.class, client::connect);
        assertEquals(ConnectionState.DISCONNECTED, client.state());
    }

    @Test
    void serverShutdownMovesClientToDisconnectedWithoutBlockingCaller() throws Exception {
        startServer();
        connectClient("localhost");

        server.close();
        await(() -> client.state() == ConnectionState.DISCONNECTED);

        assertEquals(ConnectionState.DISCONNECTED, client.state());
    }

    private AuthClient connectClient(String expectedHost) throws IOException {
        ClientConfig config = clientConfig(expectedHost);
        client = new TcpClient(config, TrustStoreLoader.load(config));
        AuthClient authClient = new AuthClient(client);
        client.connect();
        return authClient;
    }

    private void startServer() throws IOException {
        ServerConfig config = new ServerConfig(
                "127.0.0.1", 0, 10, 4, Duration.ofSeconds(3),
                new ServerConfig.TlsConfig(keystore, "PKCS12", KEYSTORE_PASSWORD, new String[]{"TLSv1.3", "TLSv1.2"}, false),
                new ServerConfig.ProtocolConfig(65_536, StandardCharsets.UTF_8, 32_768),
                new ServerConfig.AuthConfig(4, 5, Duration.ofSeconds(60)),
                new ServerConfig.DatabaseConfig("jdbc:unused", "unused", "unused", "schema.sql", 1, 0,
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1)));
        server = new ChatServer(config, SslContextFactory.create(config.tls()), new AuthService(
                new InMemoryUserRepository(), new PasswordHasher(4), new LoginAttemptLimiter(5, Duration.ofSeconds(60))));
        server.start();
    }

    private ClientConfig clientConfig(String expectedHost) {
        Properties properties = new Properties();
        properties.setProperty("server.host", "127.0.0.1");
        properties.setProperty("server.port", String.valueOf(server.port()));
        properties.setProperty("connect.timeoutMillis", "3000");
        properties.setProperty("tls.truststore.path", certificate.toString());
        properties.setProperty("tls.protocols", "TLSv1.3,TLSv1.2");
        properties.setProperty("tls.expectedHost", expectedHost);
        properties.setProperty("ui.title", "Test");
        return ClientConfig.from(properties);
    }

    private static void runKeytool(Path keytool, String... arguments) throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = keytool.toString();
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }

    private static void await(Condition condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.matches()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for client connection state");
            }
            Thread.sleep(20);
        }
    }

    @FunctionalInterface
    private interface Condition {
        boolean matches();
    }

    private static final class InMemoryUserRepository extends UserRepository {
        private final Map<String, UserAccount> users = new ConcurrentHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        private InMemoryUserRepository() {
            super(mock(DataSource.class));
        }

        @Override
        public Optional<UserAccount> findByUsername(String username) {
            return Optional.ofNullable(users.get(username));
        }

        @Override
        public UserAccount create(String username, String passwordHash, String displayName, long createdAt) {
            UserAccount account = new UserAccount(ids.incrementAndGet(), username, passwordHash, displayName, null);
            if (users.putIfAbsent(username, account) != null) {
                throw new UsernameAlreadyExistsException();
            }
            return account;
        }
    }
}
