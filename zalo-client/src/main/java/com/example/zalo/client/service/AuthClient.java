package com.example.zalo.client.service;

import com.example.zalo.client.net.TcpClient;
import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.common.util.JsonUtil;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/** Correlates register/login protocol packets with asynchronous UI requests. */
public final class AuthClient {

    private final TcpClient tcpClient;
    private final ConcurrentMap<String, PendingRequest> pending = new ConcurrentHashMap<>();
    private volatile Consumer<Throwable> disconnectConsumer = ignored -> { };

    public AuthClient(TcpClient tcpClient) {
        this.tcpClient = tcpClient;
        tcpClient.addPacketConsumer(this::handlePacket);
        tcpClient.addDisconnectConsumer(this::handleDisconnect);
    }

    public CompletableFuture<AuthResponse> register(String username, String password, String displayName) {
        return register(username, password, displayName, "");
    }

    public CompletableFuture<AuthResponse> register(String username, String password, String displayName, String phoneNumber) {
        return send(MessageType.REGISTER, Map.of(
                "username", username,
                "password", password,
                "displayName", displayName,
                "phoneNumber", phoneNumber));
    }

    public CompletableFuture<AuthResponse> login(String username, String password) {
        return send(MessageType.LOGIN, Map.of("username", username, "password", password));
    }

    public void setDisconnectConsumer(Consumer<Throwable> consumer) {
        disconnectConsumer = consumer;
    }

    private CompletableFuture<AuthResponse> send(MessageType type, Map<String, String> data) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<AuthResponse> future = new CompletableFuture<>();
        pending.put(requestId, new PendingRequest(type, future));
        try {
            tcpClient.send(Packet.of(type, requestId, data));
        } catch (IOException | RuntimeException exception) {
            pending.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    private void handlePacket(Packet packet) {
        // Server push packets (presence, friend updates, messages) do not correlate to an auth request.
        if (packet.requestId() == null) {
            return;
        }
        PendingRequest request = pending.remove(packet.requestId());
        if (request == null) {
            return;
        }
        try {
            if (packet.type() == MessageType.ERROR) {
                request.future.completeExceptionally(new IOException(packet.data().path("code").asText("Server error")));
                return;
            }
            MessageType expected = request.type == MessageType.REGISTER
                    ? MessageType.REGISTER_RESULT
                    : MessageType.LOGIN_RESULT;
            if (packet.type() != expected) {
                request.future.completeExceptionally(new IOException("Unexpected server response"));
                return;
            }
            boolean successful = packet.data().path("success").asBoolean(false);
            UserDto user = successful ? JsonUtil.treeToValue(packet.data().path("user"), UserDto.class) : null;
            AuthResponse response = new AuthResponse(successful, packet.data().path("message").asText(""), user);
            if (successful && request.type == MessageType.LOGIN) {
                tcpClient.markAuthenticated();
            }
            request.future.complete(response);
        } catch (RuntimeException exception) {
            request.future.completeExceptionally(exception);
        }
    }

    private void handleDisconnect(Throwable cause) {
        IOException disconnected = new IOException("Disconnected from server", cause);
        pending.forEach((requestId, request) -> request.future.completeExceptionally(disconnected));
        pending.clear();
        disconnectConsumer.accept(cause);
    }

    private record PendingRequest(MessageType type, CompletableFuture<AuthResponse> future) {
    }
}
