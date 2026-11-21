package com.example.zalo.server.handler;

import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.session.ClientSession;

import java.io.IOException;

@FunctionalInterface
public interface PacketHandler {
    void handle(ClientSession session, Packet packet) throws IOException;
}
