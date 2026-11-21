package com.example.zalo.server.handler;

import com.example.zalo.common.protocol.ErrorCode;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.service.MessageService;
import com.example.zalo.server.session.ClientSession;
import com.example.zalo.server.session.SessionRegistry;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Marks a delivered message as seen, then notifies the original sender. */
public final class MarkSeenHandler implements PacketHandler {

    private final MessageService messageService;
    private final SessionRegistry sessionRegistry;
    private final PacketWriter packetWriter;

    public MarkSeenHandler(MessageService messageService, SessionRegistry sessionRegistry, PacketWriter packetWriter) {
        this.messageService = Objects.requireNonNull(messageService, "messageService must not be null");
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
        this.packetWriter = Objects.requireNonNull(packetWriter, "packetWriter must not be null");
    }

    @Override
    public void handle(ClientSession session, Packet packet) throws IOException {
        try {
            MessageService.ReceiptUpdate update = messageService.markSeen(
                    packet.data().path("messageId").asLong(-1),
                    packet.data().path("conversationId").asLong(-1),
                    session.authenticatedUser().id());
            ReceiptNotifier.notifySender(update, sessionRegistry, packetWriter);
        } catch (MessageService.UnauthorizedConversationException exception) {
            packetWriter.write(session, Packet.of(com.example.zalo.common.protocol.MessageType.ERROR, packet.requestId(), Map.of(
                    "code", ErrorCode.UNAUTHORIZED.name(), "message", "Không có quyền cập nhật trạng thái.")));
        } catch (IllegalArgumentException exception) {
            packetWriter.write(session, Packet.of(com.example.zalo.common.protocol.MessageType.ERROR, packet.requestId(), Map.of(
                    "code", ErrorCode.NOT_FOUND.name(), "message", "Không tìm thấy tin nhắn.")));
        }
    }
}
