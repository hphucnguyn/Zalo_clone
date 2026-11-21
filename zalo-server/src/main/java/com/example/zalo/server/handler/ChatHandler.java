package com.example.zalo.server.handler;

import com.example.zalo.common.protocol.ErrorCode;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.service.MessageService;
import com.example.zalo.server.session.ClientSession;
import com.example.zalo.server.session.SessionRegistry;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Persists a text message first, then delivers it to every online conversation member. */
public final class ChatHandler implements PacketHandler {

    private final MessageService messageService;
    private final SessionRegistry sessionRegistry;
    private final PacketWriter packetWriter;

    public ChatHandler(MessageService messageService, SessionRegistry sessionRegistry, PacketWriter packetWriter) {
        this.messageService = Objects.requireNonNull(messageService, "messageService must not be null");
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
        this.packetWriter = Objects.requireNonNull(packetWriter, "packetWriter must not be null");
    }

    @Override
    public void handle(ClientSession session, Packet packet) throws IOException {
        try {
            MessageService.SavedMessage saved = messageService.sendText(
                    session.authenticatedUser(),
                    packet.data().path("conversationId").asLong(-1),
                    packet.data().path("content").asText(null));
            Packet delivered = Packet.of(MessageType.MESSAGE, packet.requestId(), saved.message());
            for (long userId : saved.recipientIds()) {
                for (ClientSession recipient : sessionRegistry.findByUserId(userId)) {
                    try {
                        packetWriter.write(recipient, delivered);
                        if (recipient.authenticatedUser().id() != saved.message().senderId()) {
                            ReceiptNotifier.notifySender(messageService.markDelivered(
                                    saved.message().id(), saved.message().conversationId(), recipient.authenticatedUser().id()),
                                    sessionRegistry, packetWriter);
                        }
                    } catch (IOException ignored) {
                        sessionRegistry.unregister(recipient);
                    }
                }
            }
        } catch (MessageService.UnauthorizedConversationException exception) {
            sendError(session, packet.requestId(), ErrorCode.UNAUTHORIZED, "Không có quyền gửi vào hội thoại này.");
        } catch (IllegalArgumentException exception) {
            sendError(session, packet.requestId(), ErrorCode.VALIDATION_FAILED, "Nội dung tin nhắn không hợp lệ.");
        }
    }

    private void sendError(ClientSession session, String requestId, ErrorCode code, String message) throws IOException {
        packetWriter.write(session, Packet.of(MessageType.ERROR, requestId, Map.of("code", code.name(), "message", message)));
    }
}
