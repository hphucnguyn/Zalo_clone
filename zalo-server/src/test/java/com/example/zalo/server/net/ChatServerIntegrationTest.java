package com.example.zalo.server.net;

import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.common.util.JsonUtil;
import com.example.zalo.server.config.ServerConfig;
import com.example.zalo.server.config.SslContextFactory;
import com.example.zalo.server.repository.UserRepository;
import com.example.zalo.server.repository.ConversationRepository;
import com.example.zalo.server.repository.MessageRepository;
import com.example.zalo.server.security.LoginAttemptLimiter;
import com.example.zalo.server.security.PasswordHasher;
import com.example.zalo.server.service.AuthService;
import com.example.zalo.server.service.ConversationService;
import com.example.zalo.server.service.MessageService;
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
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChatServerIntegrationTest {

    private static final String KEYSTORE_PASSWORD = "test-password";
    private static Path temporaryDirectory;
    private static Path keystore;
    private ChatServer server;
    private InMemoryConversationRepository conversations;
    private InMemoryMessageRepository messages;

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
    void registerThenLoginAuthenticatesSessionAndAllowsPing() throws Exception {
        startServer();
        try (SSLSocket socket = tlsSocket()) {
            socket.startHandshake();
            write(socket, JsonUtil.toJson(Packet.of(MessageType.REGISTER, "register-1", Map.of(
                    "username", "alice", "password", "correct-password", "displayName", "Alice"))));
            Packet registration = JsonUtil.fromJson(read(socket));
            assertEquals(MessageType.REGISTER_RESULT, registration.type());
            assertEquals(true, registration.data().get("success").asBoolean());

            write(socket, JsonUtil.toJson(Packet.of(MessageType.LOGIN, "login-1", Map.of(
                    "username", "alice", "password", "correct-password"))));
            Packet login = JsonUtil.fromJson(read(socket));
            assertEquals(MessageType.LOGIN_RESULT, login.type());
            assertEquals(true, login.data().get("success").asBoolean());

            write(socket, JsonUtil.toJson(Packet.of(MessageType.PING, "ping-1", Map.of())));

            Packet response = JsonUtil.fromJson(read(socket));

            assertEquals(MessageType.PONG, response.type());
            assertEquals("ping-1", response.requestId());
        }
    }

    @Test
    void wrongPasswordReturnsGenericLoginFailureAndLocksAfterFiveAttempts() throws Exception {
        startServer();
        try (SSLSocket socket = tlsSocket()) {
            socket.startHandshake();
            write(socket, JsonUtil.toJson(Packet.of(MessageType.REGISTER, "register-1", Map.of(
                    "username", "bob", "password", "correct-password", "displayName", "Bob"))));
            read(socket);

            for (int attempt = 1; attempt <= 5; attempt++) {
                write(socket, JsonUtil.toJson(Packet.of(MessageType.LOGIN, "login-" + attempt, Map.of(
                        "username", "bob", "password", "incorrect-password"))));
                Packet response = JsonUtil.fromJson(read(socket));
                assertEquals(MessageType.LOGIN_RESULT, response.type());
                assertEquals(false, response.data().get("success").asBoolean());
                String message = response.data().get("message").asText();
                if (attempt < 5) {
                    assertEquals("Tên đăng nhập hoặc mật khẩu không đúng.", message);
                } else {
                    assertEquals("Tài khoản tạm thời bị khóa. Vui lòng thử lại sau.", message);
                }
            }
        }
    }

    @Test
    void unauthenticatedMessagePacketReceivesNotAuthenticatedError() throws Exception {
        startServer();
        try (SSLSocket socket = tlsSocket()) {
            socket.startHandshake();
            write(socket, JsonUtil.toJson(Packet.of(MessageType.SEND_MESSAGE, "message-1", Map.of("content", "secret"))));

            Packet response = JsonUtil.fromJson(read(socket));

            assertEquals(MessageType.ERROR, response.type());
            assertEquals("NOT_AUTHENTICATED", response.data().get("code").asText());
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

    @Test
    void directMessageIsSavedBeforeDeliveryAndOfflineRecipientDoesNotPreventSaving() throws Exception {
        startServer();
        try (SSLSocket alice = tlsSocket(); SSLSocket bob = tlsSocket()) {
            alice.startHandshake();
            bob.startHandshake();
            long aliceId = registerAndLogin(alice, "alice_chat", "Alice");
            long bobId = registerAndLogin(bob, "bob_chat", "Bob");

            write(alice, JsonUtil.toJson(Packet.of(MessageType.OPEN_DIRECT, "open-1", Map.of("username", "bob_chat"))));
            Packet opened = JsonUtil.fromJson(read(alice));
            assertEquals(MessageType.DIRECT_OPENED, opened.type());
            assertEquals(true, opened.data().path("success").asBoolean());
            long conversationId = opened.data().path("conversation").path("id").asLong();

            write(alice, JsonUtil.toJson(Packet.of(MessageType.SEND_MESSAGE, "send-1", Map.of(
                    "conversationId", conversationId, "content", "Xin chào Bob!"))));
            Packet senderDelivery = JsonUtil.fromJson(read(alice));
            Packet recipientDelivery = JsonUtil.fromJson(read(bob));
            assertEquals(MessageType.MESSAGE, senderDelivery.type());
            assertEquals(MessageType.MESSAGE, recipientDelivery.type());
            assertEquals(aliceId, recipientDelivery.data().path("senderId").asLong());
            assertEquals("Alice", recipientDelivery.data().path("senderName").asText());
            assertEquals("Xin chào Bob!", recipientDelivery.data().path("content").asText());
            assertEquals(conversationId, recipientDelivery.data().path("conversationId").asLong());
            assertEquals(true, recipientDelivery.data().path("sentAt").asLong() > 0);
            assertEquals(1, messages.count());

            bob.close();
            await(() -> server.sessionRegistry().findByUserId(bobId).isEmpty());
            write(alice, JsonUtil.toJson(Packet.of(MessageType.SEND_MESSAGE, "send-2", Map.of(
                    "conversationId", conversationId, "content", "Tin nhắn khi Bob offline"))));
            Packet offlineDelivery = JsonUtil.fromJson(read(alice));
            assertEquals(MessageType.MESSAGE, offlineDelivery.type());
            assertEquals("Tin nhắn khi Bob offline", offlineDelivery.data().path("content").asText());
            assertEquals(2, messages.count());
        }
    }

    @Test
    void historyUsesBeforeAndAfterCursorsWithoutDuplicatingRows() throws Exception {
        startServer();
        try (SSLSocket alice = tlsSocket(); SSLSocket bob = tlsSocket()) {
            alice.startHandshake();
            bob.startHandshake();
            registerAndLogin(alice, "alice_history", "Alice");
            registerAndLogin(bob, "bob_history", "Bob");
            write(alice, JsonUtil.toJson(Packet.of(MessageType.OPEN_DIRECT, "open-history", Map.of("username", "bob_history"))));
            long conversationId = JsonUtil.fromJson(read(alice)).data().path("conversation").path("id").asLong();

            for (int index = 1; index <= 3; index++) {
                write(alice, JsonUtil.toJson(Packet.of(MessageType.SEND_MESSAGE, "send-history-" + index, Map.of(
                        "conversationId", conversationId, "content", "message-" + index))));
                read(alice);
                read(bob);
            }

            write(alice, JsonUtil.toJson(Packet.of(MessageType.HISTORY_REQUEST, "latest", Map.of(
                    "conversationId", conversationId, "limit", 2))));
            Packet latest = JsonUtil.fromJson(read(alice));
            assertEquals(MessageType.HISTORY_RESPONSE, latest.type());
            assertEquals(true, latest.data().path("hasMore").asBoolean());
            assertEquals("message-2", latest.data().path("messages").get(0).path("content").asText());
            assertEquals("message-3", latest.data().path("messages").get(1).path("content").asText());

            long beforeId = latest.data().path("messages").get(0).path("id").asLong();
            write(alice, JsonUtil.toJson(Packet.of(MessageType.HISTORY_REQUEST, "older", Map.of(
                    "conversationId", conversationId, "beforeId", beforeId, "limit", 2))));
            Packet older = JsonUtil.fromJson(read(alice));
            assertEquals(false, older.data().path("hasMore").asBoolean());
            assertEquals("message-1", older.data().path("messages").get(0).path("content").asText());

            long latestId = latest.data().path("messages").get(1).path("id").asLong();
            write(alice, JsonUtil.toJson(Packet.of(MessageType.HISTORY_REQUEST, "newer", Map.of(
                    "conversationId", conversationId, "afterId", latestId, "limit", 50))));
            Packet newer = JsonUtil.fromJson(read(alice));
            assertEquals(0, newer.data().path("messages").size());
        }
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
                new ServerConfig.AuthConfig(4, 5, Duration.ofSeconds(60)),
                new ServerConfig.DatabaseConfig("jdbc:unused", "unused", "unused", "schema.sql", 1, 0,
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1)));
        InMemoryUserRepository users = new InMemoryUserRepository();
        conversations = new InMemoryConversationRepository();
        messages = new InMemoryMessageRepository();
        server = new ChatServer(config, SslContextFactory.create(config.tls()), new AuthService(
                users,
                new PasswordHasher(config.auth().bcryptRounds()),
                new LoginAttemptLimiter(config.auth().maxLoginAttempts(), config.auth().lockDuration())),
                new ConversationService(users, conversations),
                new MessageService(conversations, messages));
        server.start();
    }

    private static long registerAndLogin(SSLSocket socket, String username, String displayName) throws Exception {
        write(socket, JsonUtil.toJson(Packet.of(MessageType.REGISTER, "register-" + username, Map.of(
                "username", username, "password", "correct-password", "displayName", displayName))));
        Packet registration = JsonUtil.fromJson(read(socket));
        assertEquals(true, registration.data().path("success").asBoolean());
        write(socket, JsonUtil.toJson(Packet.of(MessageType.LOGIN, "login-" + username, Map.of(
                "username", username, "password", "correct-password"))));
        Packet login = JsonUtil.fromJson(read(socket));
        assertEquals(true, login.data().path("success").asBoolean());
        return login.data().path("user").path("id").asLong();
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

    private static final class InMemoryConversationRepository extends ConversationRepository {
        private final AtomicLong ids = new AtomicLong();
        private final Map<Set<Long>, Long> conversations = new ConcurrentHashMap<>();
        private final Map<Long, Set<Long>> members = new ConcurrentHashMap<>();

        private InMemoryConversationRepository() {
            super(mock(DataSource.class));
        }

        @Override
        public long findOrCreateDirect(long firstUserId, long secondUserId, long createdAt) {
            Set<Long> users = Set.of(firstUserId, secondUserId);
            long id = conversations.computeIfAbsent(users, ignored -> ids.incrementAndGet());
            members.putIfAbsent(id, users);
            return id;
        }

        @Override
        public boolean isMember(long conversationId, long userId) {
            return members.getOrDefault(conversationId, Set.of()).contains(userId);
        }

        @Override
        public long[] memberIds(long conversationId) {
            return members.getOrDefault(conversationId, Set.of()).stream().mapToLong(Long::longValue).toArray();
        }
    }

    private static final class InMemoryMessageRepository extends MessageRepository {
        private final AtomicLong ids = new AtomicLong();
        private final List<StoredMessage> messages = new ArrayList<>();

        private InMemoryMessageRepository() {
            super(mock(DataSource.class));
        }

        @Override
        public synchronized long insertText(long conversationId, long senderId, String content, long sentAt) {
            long id = ids.incrementAndGet();
            messages.add(new StoredMessage(id, conversationId, senderId, content, sentAt));
            return id;
        }

        synchronized int count() {
            return messages.size();
        }

        @Override
        public synchronized List<com.example.zalo.common.model.MessageDto> findPage(
                long conversationId, long beforeId, long afterId, int limit) {
            java.util.stream.Stream<StoredMessage> stream = messages.stream()
                    .filter(message -> message.conversationId == conversationId);
            if (afterId > 0) {
                stream = stream.filter(message -> message.id > afterId).sorted(java.util.Comparator.comparingLong(message -> message.id));
            } else {
                stream = stream.filter(message -> beforeId <= 0 || message.id < beforeId)
                        .sorted(java.util.Comparator.comparingLong((StoredMessage message) -> message.id).reversed());
            }
            return stream.limit(limit + 1L)
                    .map(message -> new com.example.zalo.common.model.MessageDto(
                            message.id, message.conversationId, message.senderId, "Sender-" + message.senderId,
                            message.content, message.sentAt))
                    .toList();
        }

        private record StoredMessage(long id, long conversationId, long senderId, String content, long sentAt) {
        }
    }
}
