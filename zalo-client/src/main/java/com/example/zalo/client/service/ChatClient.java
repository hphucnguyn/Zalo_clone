package com.example.zalo.client.service;

import com.example.zalo.client.net.TcpClient;
import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.MessageDto;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.common.util.JsonUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Client-side protocol facade for direct conversations and realtime messages. */
public final class ChatClient {

    private final TcpClient tcpClient;
    private final ConcurrentMap<String, CompletableFuture<ConversationDto>> pendingDirects = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, CopyOnWriteArrayList<MessageDto>> messagesByConversation = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<MessageDto>> messageConsumers = new CopyOnWriteArrayList<>();

    public ChatClient(TcpClient tcpClient) {
        this.tcpClient = tcpClient;
        tcpClient.addPacketConsumer(this::handlePacket);
        tcpClient.addDisconnectConsumer(this::handleDisconnect);
    }

    public CompletableFuture<ConversationDto> openDirect(String username) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<ConversationDto> future = new CompletableFuture<>();
        pendingDirects.put(requestId, future);
        try {
            tcpClient.send(Packet.of(MessageType.OPEN_DIRECT, requestId, Map.of("username", username)));
        } catch (IOException exception) {
            pendingDirects.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public void sendText(long conversationId, String content) throws IOException {
        tcpClient.send(Packet.of(MessageType.SEND_MESSAGE, UUID.randomUUID().toString(), Map.of(
                "conversationId", conversationId,
                "content", content)));
    }

    public List<MessageDto> messages(long conversationId) {
        return List.copyOf(messagesByConversation.getOrDefault(conversationId, new CopyOnWriteArrayList<>()));
    }

    public void addMessageConsumer(Consumer<MessageDto> consumer) {
        messageConsumers.add(consumer);
    }

    private void handlePacket(Packet packet) {
        if (packet.type() == MessageType.DIRECT_OPENED) {
            CompletableFuture<ConversationDto> pending = pendingDirects.remove(packet.requestId());
            if (pending == null) {
                return;
            }
            if (!packet.data().path("success").asBoolean(false)) {
                pending.completeExceptionally(new IOException(packet.data().path("message").asText("Không thể mở hội thoại.")));
                return;
            }
            pending.complete(JsonUtil.treeToValue(packet.data().path("conversation"), ConversationDto.class));
            return;
        }
        if (packet.type() == MessageType.MESSAGE) {
            MessageDto message = JsonUtil.treeToValue(packet.data(), MessageDto.class);
            messagesByConversation.computeIfAbsent(message.conversationId(), ignored -> new CopyOnWriteArrayList<>()).add(message);
            for (Consumer<MessageDto> consumer : messageConsumers) {
                consumer.accept(message);
            }
        }
    }

    private void handleDisconnect(Throwable cause) {
        IOException disconnected = new IOException("Disconnected from server", cause);
        pendingDirects.forEach((id, future) -> future.completeExceptionally(disconnected));
        pendingDirects.clear();
    }
}
