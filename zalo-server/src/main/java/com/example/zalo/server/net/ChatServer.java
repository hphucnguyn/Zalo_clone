package com.example.zalo.server.net;

import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.server.config.ServerConfig;
import com.example.zalo.server.handler.PacketDispatcher;
import com.example.zalo.server.handler.PingHandler;
import com.example.zalo.server.handler.LoginHandler;
import com.example.zalo.server.handler.RegisterHandler;
import com.example.zalo.server.handler.ConversationHandler;
import com.example.zalo.server.handler.ChatHandler;
import com.example.zalo.server.handler.HistoryHandler;
import com.example.zalo.server.handler.MarkSeenHandler;
import com.example.zalo.server.service.AuthService;
import com.example.zalo.server.service.ConversationService;
import com.example.zalo.server.service.MessageService;
import com.example.zalo.server.service.PresenceService;
import com.example.zalo.server.repository.ConversationRepository;
import com.example.zalo.server.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.SocketException;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** TLS accept loop and authentication packet routing. */
public final class ChatServer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatServer.class);

    private final ServerConfig config;
    private final SSLContext sslContext;
    private final SessionRegistry sessionRegistry = new SessionRegistry();
    private final PacketWriter packetWriter = new PacketWriter();
    private final PacketDispatcher dispatcher;
    private final ExecutorService clientExecutor;
    private final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Set<SSLSocket> acceptedSockets = ConcurrentHashMap.newKeySet();
    private volatile SSLServerSocket serverSocket;

    public ChatServer(ServerConfig config, SSLContext sslContext, AuthService authService) {
        this(config, sslContext, authService, null, null);
    }

    public ChatServer(
            ServerConfig config,
            SSLContext sslContext,
            AuthService authService,
            ConversationService conversationService,
            MessageService messageService) {
        this(config, sslContext, authService, conversationService, messageService, null);
    }

    public ChatServer(
            ServerConfig config,
            SSLContext sslContext,
            AuthService authService,
            ConversationService conversationService,
            MessageService messageService,
            ConversationRepository presenceConversationRepository) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.sslContext = Objects.requireNonNull(sslContext, "sslContext must not be null");
        Objects.requireNonNull(authService, "authService must not be null");
        PresenceService presenceService = presenceConversationRepository == null ? null
                : new PresenceService(sessionRegistry, presenceConversationRepository, packetWriter);
        PacketDispatcher packetDispatcher = new PacketDispatcher(packetWriter)
                .register(MessageType.REGISTER, new RegisterHandler(authService, packetWriter))
                .register(MessageType.LOGIN, new LoginHandler(authService, packetWriter, presenceService))
                .register(MessageType.PING, new PingHandler(packetWriter));
        if (conversationService != null && messageService != null) {
            packetDispatcher
                    .register(MessageType.OPEN_DIRECT, new ConversationHandler(conversationService, packetWriter))
                    .register(MessageType.SEND_MESSAGE, new ChatHandler(messageService, sessionRegistry, packetWriter))
                    .register(MessageType.HISTORY_REQUEST, new HistoryHandler(messageService, packetWriter))
                    .register(MessageType.MARK_SEEN, new MarkSeenHandler(messageService, sessionRegistry, packetWriter));
        }
        this.dispatcher = packetDispatcher;
        this.clientExecutor = Executors.newFixedThreadPool(config.maxClients());
    }

    public synchronized void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Chat server is already running");
        }
        try {
            SSLServerSocket socket = (SSLServerSocket) sslContext.getServerSocketFactory()
                    .createServerSocket(config.port(), config.backlog());
            socket.setEnabledProtocols(config.tls().protocols());
            socket.setNeedClientAuth(config.tls().needClientAuth());
            serverSocket = socket;
            acceptExecutor.execute(this::acceptLoop);
        } catch (IOException | RuntimeException exception) {
            running.set(false);
            throw exception;
        }
    }

    public int port() {
        SSLServerSocket socket = serverSocket;
        if (socket == null) {
            throw new IllegalStateException("Chat server has not been started");
        }
        return socket.getLocalPort();
    }

    public SessionRegistry sessionRegistry() {
        return sessionRegistry;
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                SSLSocket socket = (SSLSocket) serverSocket.accept();
                acceptedSockets.add(socket);
                socket.setSoTimeout((int) config.readTimeout().toMillis());
                clientExecutor.execute(() -> {
                    try {
                        new ClientHandler(
                                socket,
                                sessionRegistry,
                                dispatcher,
                                packetWriter,
                                config.protocol().maxLineBytes(),
                                config.protocol().charset(),
                                session -> {
                                    if (presenceService != null && sessionRegistry.findByUserId(session.authenticatedUser().id()).isEmpty()) {
                                        presenceService.publish(session.authenticatedUser(), false);
                                    }
                                }).run();
                    } finally {
                        acceptedSockets.remove(socket);
                    }
                });
            } catch (SocketException exception) {
                if (running.get()) {
                    LOGGER.warn("TLS accept loop stopped unexpectedly", exception);
                }
            } catch (IOException exception) {
                if (running.get()) {
                    LOGGER.warn("Could not accept TLS client", exception);
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException exception) {
            LOGGER.debug("Could not close TLS server socket", exception);
        }
        sessionRegistry.closeAll();
        for (SSLSocket socket : acceptedSockets) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // All sockets must receive a best-effort shutdown signal.
            }
        }
        acceptedSockets.clear();
        acceptExecutor.shutdownNow();
        clientExecutor.shutdownNow();
    }
}
