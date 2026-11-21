package com.example.zalo.server.handler;

import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.protocol.ErrorCode;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.service.ConversationService;
import com.example.zalo.server.session.ClientSession;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Opens a direct conversation by recipient username. */
public final class ConversationHandler implements PacketHandler {

    private final ConversationService conversationService;
    private final PacketWriter packetWriter;

    public ConversationHandler(ConversationService conversationService, PacketWriter packetWriter) {
        this.conversationService = Objects.requireNonNull(conversationService, "conversationService must not be null");
        this.packetWriter = Objects.requireNonNull(packetWriter, "packetWriter must not be null");
    }

    @Override
    public void handle(ClientSession session, Packet packet) throws IOException {
        try {
            ConversationDto conversation = conversationService.openDirect(
                    session.authenticatedUser(), packet.data().path("username").asText(null));
            packetWriter.write(session, Packet.of(MessageType.DIRECT_OPENED, packet.requestId(), Map.of(
                    "success", true,
                    "conversation", conversation)));
        } catch (ConversationService.ConversationNotFoundException exception) {
            packetWriter.write(session, Packet.of(MessageType.DIRECT_OPENED, packet.requestId(), Map.of(
                    "success", false,
                    "message", "Không tìm thấy người dùng.")));
        } catch (IllegalArgumentException exception) {
            packetWriter.write(session, Packet.of(MessageType.ERROR, packet.requestId(), Map.of(
                    "code", ErrorCode.VALIDATION_FAILED.name(),
                    "message", "Không thể mở hội thoại.")));
        }
    }
}
