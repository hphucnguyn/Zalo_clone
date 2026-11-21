package com.example.zalo.server.handler;

import com.example.zalo.common.protocol.ErrorCode;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.session.ClientSession;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Routes packets to their handler; unsupported types receive a protocol error. */
public final class PacketDispatcher {

    private final Map<MessageType, PacketHandler> handlers = new EnumMap<>(MessageType.class);
    private final PacketWriter packetWriter;

    public PacketDispatcher(PacketWriter packetWriter) {
        this.packetWriter = Objects.requireNonNull(packetWriter, "packetWriter must not be null");
    }

    public PacketDispatcher register(MessageType type, PacketHandler handler) {
        handlers.put(Objects.requireNonNull(type, "type must not be null"),
                Objects.requireNonNull(handler, "handler must not be null"));
        return this;
    }

    public void dispatch(ClientSession session, Packet packet) throws IOException {
        PacketHandler handler = handlers.get(packet.type());
        if (handler == null) {
            sendError(session, packet.requestId(), ErrorCode.UNKNOWN_TYPE, "Message type is not available yet");
            return;
        }
        handler.handle(session, packet);
    }

    public void sendError(ClientSession session, String requestId, ErrorCode code, String message) throws IOException {
        packetWriter.write(session, Packet.of(MessageType.ERROR, requestId, Map.of(
                "code", code.name(),
                "message", message)));
    }
}
