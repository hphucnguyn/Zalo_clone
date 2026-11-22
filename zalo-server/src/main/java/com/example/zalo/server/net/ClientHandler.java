package com.example.zalo.server.net;

import com.example.zalo.common.protocol.ErrorCode;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.common.util.JsonUtil;
import com.example.zalo.server.handler.PacketDispatcher;
import com.example.zalo.server.session.ClientSession;
import com.example.zalo.server.session.ClientState;
import com.example.zalo.server.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Consumer;

/** Performs TLS handshake, reads newline-delimited packets, and always releases its session. */
public final class ClientHandler implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);

    private final SSLSocket socket;
    private final SessionRegistry sessionRegistry;
    private final PacketDispatcher dispatcher;
    private final PacketWriter packetWriter;
    private final int maxLineBytes;
    private final Charset charset;
    private final Consumer<ClientSession> disconnectConsumer;

    public ClientHandler(
            SSLSocket socket,
            SessionRegistry sessionRegistry,
            PacketDispatcher dispatcher,
            PacketWriter packetWriter,
            int maxLineBytes,
            Charset charset) {
        this(socket, sessionRegistry, dispatcher, packetWriter, maxLineBytes, charset, ignored -> { });
    }

    public ClientHandler(
            SSLSocket socket,
            SessionRegistry sessionRegistry,
            PacketDispatcher dispatcher,
            PacketWriter packetWriter,
            int maxLineBytes,
            Charset charset,
            Consumer<ClientSession> disconnectConsumer) {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.packetWriter = Objects.requireNonNull(packetWriter, "packetWriter must not be null");
        this.maxLineBytes = maxLineBytes;
        this.charset = Objects.requireNonNull(charset, "charset must not be null");
        this.disconnectConsumer = Objects.requireNonNull(disconnectConsumer, "disconnectConsumer must not be null");
    }

    @Override
    public void run() {
        ClientSession session = null;
        try {
            socket.setUseClientMode(false);
            socket.startHandshake();
            session = new ClientSession(socket,
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), charset)),
                    new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), charset)));
            session.setState(ClientState.TLS_READY);
            sessionRegistry.register(session);
            processPackets(session);
        } catch (SSLException exception) {
            LOGGER.debug("Rejected a connection that did not complete TLS handshake");
        } catch (IOException exception) {
            LOGGER.debug("Client connection closed: {}", exception.getMessage());
        } finally {
            if (session != null) {
                sessionRegistry.unregister(session);
                if (session.authenticatedUser() != null) {
                    disconnectConsumer.accept(session);
                }
            } else {
                closeSocket();
            }
        }
    }

    private void processPackets(ClientSession session) throws IOException {
        while (!socket.isClosed()) {
            String line;
            try {
                line = readLine(session.reader());
            } catch (LineTooLargeException exception) {
                dispatcher.sendError(session, null, ErrorCode.TOO_LARGE, "JSON line exceeds the configured limit");
                return;
            }
            if (line == null) {
                return;
            }

            Packet packet;
            try {
                packet = JsonUtil.fromJson(line);
            } catch (IllegalArgumentException exception) {
                dispatcher.sendError(session, null, ErrorCode.INVALID_JSON, "Packet must be valid one-line JSON");
                continue;
            }

            if (packet.type() == MessageType.DISCONNECT) {
                return;
            }
            if (session.state() != ClientState.AUTHENTICATED
                    && packet.type() != MessageType.REGISTER
                    && packet.type() != MessageType.LOGIN) {
                dispatcher.sendError(session, packet.requestId(), ErrorCode.NOT_AUTHENTICATED,
                        "Authentication is required");
                continue;
            }
            try {
                dispatcher.dispatch(session, packet);
            } catch (RuntimeException exception) {
                LOGGER.error("Packet handler failed for type {}", packet.type(), exception);
                dispatcher.sendError(session, packet.requestId(), ErrorCode.INTERNAL_ERROR,
                        "Yêu cầu chưa xử lý được. Hãy thử lại sau.");
            }
        }
    }

    private String readLine(BufferedReader reader) throws IOException, LineTooLargeException {
        StringBuilder line = new StringBuilder();
        int next;
        while ((next = reader.read()) != -1) {
            if (next == '\n') {
                break;
            }
            if (next != '\r') {
                line.append((char) next);
            }
            if (line.length() > maxLineBytes) {
                throw new LineTooLargeException();
            }
        }
        if (next == -1 && line.isEmpty()) {
            return null;
        }
        if (line.toString().getBytes(charset).length > maxLineBytes) {
            throw new LineTooLargeException();
        }
        return line.toString();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Connection cleanup is best effort.
        }
    }

    private static final class LineTooLargeException extends Exception {
    }
}
