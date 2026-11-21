package com.example.zalo.server.handler;

import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.session.ClientSession;

import java.io.IOException;
import java.util.Objects;

/** Responds to protocol heartbeats. */
public final class PingHandler implements PacketHandler {

    private final PacketWriter packetWriter;

    public PingHandler(PacketWriter packetWriter) {
        this.packetWriter = Objects.requireNonNull(packetWriter, "packetWriter must not be null");
    }

    @Override
    public void handle(ClientSession session, Packet packet) throws IOException {
        packetWriter.write(session, Packet.of(MessageType.PONG, packet.requestId(), null));
    }
}
