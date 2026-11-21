package com.example.zalo.server.handler;

import com.example.zalo.common.protocol.ErrorCode;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.service.MessageService;
import com.example.zalo.server.session.ClientSession;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Returns a cursor page of persisted messages to an authenticated conversation member. */
public final class HistoryHandler implements PacketHandler {

    private final MessageService messageService;
    private final PacketWriter packetWriter;

    public HistoryHandler(MessageService messageService, PacketWriter packetWriter) {
        this.messageService = Objects.requireNonNull(messageService, "messageService must not be null");
        this.packetWriter = Objects.requireNonNull(packetWriter, "packetWriter must not be null");
    }

    @Override
    public void handle(ClientSession session, Packet packet) throws IOException {
        try {
            MessageService.HistoryPage page = messageService.history(
                    session.authenticatedUser(),
                    packet.data().path("conversationId").asLong(-1),
                    packet.data().path("beforeId").asLong(0),
                    packet.data().path("afterId").asLong(0),
                    packet.data().path("limit").asInt(50));
            packetWriter.write(session, Packet.of(MessageType.HISTORY_RESPONSE, packet.requestId(), Map.of(
                    "messages", page.messages(),
                    "hasMore", page.hasMore())));
        } catch (MessageService.UnauthorizedConversationException exception) {
            sendError(session, packet.requestId(), ErrorCode.UNAUTHORIZED, "Không có quyền xem hội thoại này.");
        } catch (IllegalArgumentException exception) {
            sendError(session, packet.requestId(), ErrorCode.VALIDATION_FAILED, "Yêu cầu lịch sử không hợp lệ.");
        }
    }

    private void sendError(ClientSession session, String requestId, ErrorCode code, String message) throws IOException {
        packetWriter.write(session, Packet.of(MessageType.ERROR, requestId, Map.of("code", code.name(), "message", message)));
    }
}
