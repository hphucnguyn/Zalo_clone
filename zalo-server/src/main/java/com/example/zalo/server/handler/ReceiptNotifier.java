package com.example.zalo.server.handler;

import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.service.MessageService;
import com.example.zalo.server.session.ClientSession;
import com.example.zalo.server.session.SessionRegistry;

import java.io.IOException;
import java.util.Map;

final class ReceiptNotifier {

    private ReceiptNotifier() {
    }

    static void notifySender(MessageService.ReceiptUpdate update, SessionRegistry sessions, PacketWriter writer) {
        Packet packet = Packet.of(MessageType.MESSAGE_STATUS, null, Map.of(
                "messageId", update.messageId(),
                "conversationId", update.conversationId(),
                "recipientId", update.recipientId(),
                "status", update.status().name()));
        for (ClientSession senderSession : sessions.findByUserId(update.senderId())) {
            try {
                writer.write(senderSession, packet);
            } catch (IOException exception) {
                sessions.unregister(senderSession);
            }
        }
    }
}
