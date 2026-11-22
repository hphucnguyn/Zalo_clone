package com.example.zalo.client.service;

import com.example.zalo.client.net.TcpClient;
import com.example.zalo.client.cache.MessageCache;
import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.MessageDto;
import com.example.zalo.common.model.MessageStatus;
import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.common.util.JsonUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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
    private final ConcurrentMap<String, CompletableFuture<HistoryPage>> pendingHistory = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<ConversationDto>> pendingGroups = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, CopyOnWriteArrayList<MessageDto>> messagesByConversation = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<MessageDto>> messageConsumers = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<Long, MessageStatus> statusesByMessage = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Boolean> presenceByUser = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<Long>> statusConsumers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Long>> presenceConsumers = new CopyOnWriteArrayList<>();
    private volatile MessageCache messageCache;
    private volatile UserDto currentUser;

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
        return future.thenApply(conversation -> {
            MessageCache cache = messageCache;
            if (cache != null) {
                cache.saveConversation(conversation).exceptionally(ignored -> null);
            }
            return conversation;
        });
    }

    public void sendText(long conversationId, String content) throws IOException {
        tcpClient.send(Packet.of(MessageType.SEND_MESSAGE, UUID.randomUUID().toString(), Map.of(
                "conversationId", conversationId,
                "content", content)));
    }

    public CompletableFuture<ConversationDto> createGroup(String name) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<ConversationDto> future = new CompletableFuture<>();
        pendingGroups.put(requestId, future);
        try {
            tcpClient.send(Packet.of(MessageType.CREATE_GROUP, requestId, Map.of("name", name)));
        } catch (IOException exception) {
            pendingGroups.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public void addMember(long conversationId, String username) throws IOException {
        tcpClient.send(Packet.of(MessageType.ADD_MEMBER, UUID.randomUUID().toString(), Map.of(
                "conversationId", conversationId, "username", username)));
    }

    public void leaveGroup(long conversationId) throws IOException {
        tcpClient.send(Packet.of(MessageType.LEAVE_GROUP, UUID.randomUUID().toString(), Map.of("conversationId", conversationId)));
    }

    public CompletableFuture<HistoryPage> requestHistory(long conversationId, long beforeId, long afterId, int limit) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<HistoryPage> future = new CompletableFuture<>();
        pendingHistory.put(requestId, future);
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("limit", limit);
        if (beforeId > 0) {
            data.put("beforeId", beforeId);
        }
        if (afterId > 0) {
            data.put("afterId", afterId);
        }
        try {
            tcpClient.send(Packet.of(MessageType.HISTORY_REQUEST, requestId, data));
        } catch (IOException exception) {
            pendingHistory.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<List<MessageDto>> loadCachedMessages(long conversationId, int limit) {
        MessageCache cache = messageCache;
        if (cache == null) {
            return CompletableFuture.completedFuture(messages(conversationId));
        }
        return cache.loadRecent(conversationId, limit).thenApply(messages -> {
            mergeMessages(messages);
            return this.messages(conversationId);
        });
    }

    public CompletableFuture<List<ConversationDto>> loadCachedConversations() {
        MessageCache cache = messageCache;
        if (cache == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return cache.loadConversations();
    }

    public CompletableFuture<HistoryPage> syncNewMessages(long conversationId, int limit) {
        MessageCache cache = messageCache;
        if (cache == null) {
            long latest = messages(conversationId).stream().mapToLong(MessageDto::id).max().orElse(0);
            return requestHistory(conversationId, 0, latest, limit);
        }
        return cache.lastMessageId(conversationId)
                .thenCompose(lastId -> requestHistory(conversationId, 0, lastId, limit));
    }

    public void attachCache(MessageCache cache) {
        this.messageCache = cache;
    }

    public void setCurrentUser(UserDto user) {
        currentUser = user;
    }

    public MessageStatus status(long messageId) {
        return statusesByMessage.getOrDefault(messageId, MessageStatus.SENT);
    }

    public boolean isOnline(long userId) {
        return presenceByUser.getOrDefault(userId, false);
    }

    public void addStatusConsumer(Consumer<Long> consumer) {
        statusConsumers.add(consumer);
    }

    public void addPresenceConsumer(Consumer<Long> consumer) {
        presenceConsumers.add(consumer);
    }

    public void markSeen(MessageDto message) {
        UserDto user = currentUser;
        if (user == null || message.senderId() == user.id()) {
            return;
        }
        try {
            tcpClient.send(Packet.of(MessageType.MARK_SEEN, UUID.randomUUID().toString(), Map.of(
                    "messageId", message.id(), "conversationId", message.conversationId())));
        } catch (IOException ignored) {
            // Offline clients cannot update receipts; the next open chat can retry.
        }
    }

    public List<MessageDto> messages(long conversationId) {
        return List.copyOf(messagesByConversation.getOrDefault(conversationId, new CopyOnWriteArrayList<>()));
    }

    public void addMessageConsumer(Consumer<MessageDto> consumer) {
        messageConsumers.add(consumer);
    }

    private void handlePacket(Packet packet) {
        if (packet.type() == MessageType.ERROR) {
            IOException error = new IOException(packet.data().path("message").asText("Server error"));
            CompletableFuture<ConversationDto> direct = pendingDirects.remove(packet.requestId());
            if (direct != null) {
                direct.completeExceptionally(error);
            }
            CompletableFuture<HistoryPage> history = pendingHistory.remove(packet.requestId());
            if (history != null) {
                history.completeExceptionally(error);
            }
            CompletableFuture<ConversationDto> group = pendingGroups.remove(packet.requestId());
            if (group != null) group.completeExceptionally(error);
            return;
        }
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
        if (packet.type() == MessageType.GROUP_CREATED) {
            CompletableFuture<ConversationDto> pending = pendingGroups.remove(packet.requestId());
            if (pending != null) pending.complete(JsonUtil.treeToValue(packet.data().path("conversation"), ConversationDto.class));
            return;
        }
        if (packet.type() == MessageType.MESSAGE) {
            MessageDto message = JsonUtil.treeToValue(packet.data(), MessageDto.class);
            if (mergeMessage(message)) {
                saveToCache(List.of(message));
                notifyMessageConsumers(message);
            }
            return;
        }
        if (packet.type() == MessageType.MESSAGE_STATUS) {
            long messageId = packet.data().path("messageId").asLong(-1);
            if (messageId > 0) {
                MessageStatus status = MessageStatus.valueOf(packet.data().path("status").asText("SENT"));
                statusesByMessage.merge(messageId, status,
                        (previous, next) -> next.ordinal() > previous.ordinal() ? next : previous);
                for (Consumer<Long> consumer : statusConsumers) {
                    consumer.accept(messageId);
                }
            }
            return;
        }
        if (packet.type() == MessageType.PRESENCE) {
            long userId = packet.data().path("userId").asLong(-1);
            if (userId > 0) {
                presenceByUser.put(userId, packet.data().path("online").asBoolean(false));
                for (Consumer<Long> consumer : presenceConsumers) {
                    consumer.accept(userId);
                }
            }
            return;
        }
        if (packet.type() == MessageType.HISTORY_RESPONSE) {
            CompletableFuture<HistoryPage> pending = pendingHistory.remove(packet.requestId());
            if (pending == null) {
                return;
            }
            List<MessageDto> messages = new java.util.ArrayList<>();
            packet.data().path("messages").forEach(node -> messages.add(JsonUtil.treeToValue(node, MessageDto.class)));
            mergeMessages(messages);
            saveToCache(messages);
            pending.complete(new HistoryPage(List.copyOf(messages), packet.data().path("hasMore").asBoolean(false)));
        }
    }

    private void handleDisconnect(Throwable cause) {
        IOException disconnected = new IOException("Disconnected from server", cause);
        pendingDirects.forEach((id, future) -> future.completeExceptionally(disconnected));
        pendingDirects.clear();
        pendingHistory.forEach((id, future) -> future.completeExceptionally(disconnected));
        pendingHistory.clear();
        pendingGroups.forEach((id, future) -> future.completeExceptionally(disconnected));
        pendingGroups.clear();
    }

    private void mergeMessages(List<MessageDto> messages) {
        for (MessageDto message : messages) {
            mergeMessage(message);
        }
    }

    private boolean mergeMessage(MessageDto message) {
        CopyOnWriteArrayList<MessageDto> conversationMessages = messagesByConversation
                .computeIfAbsent(message.conversationId(), ignored -> new CopyOnWriteArrayList<>());
        if (conversationMessages.stream().anyMatch(existing -> existing.id() == message.id())) {
            return false;
        }
        conversationMessages.add(message);
        conversationMessages.sort(java.util.Comparator.comparingLong(MessageDto::id));
        return true;
    }

    private void saveToCache(List<MessageDto> messages) {
        MessageCache cache = messageCache;
        if (cache != null && !messages.isEmpty()) {
            cache.saveMessages(messages).exceptionally(ignored -> null);
        }
    }

    private void notifyMessageConsumers(MessageDto message) {
        for (Consumer<MessageDto> consumer : messageConsumers) {
            consumer.accept(message);
        }
    }
}
