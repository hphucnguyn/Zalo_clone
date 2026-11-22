package com.example.zalo.server.service;

import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.repository.ConversationRepository;
import com.example.zalo.server.session.ClientSession;
import com.example.zalo.server.session.SessionRegistry;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Publishes availability only to users that share a conversation with the changed user. */
public final class PresenceService {

    private final SessionRegistry sessionRegistry;
    private final ConversationRepository conversationRepository;
    private final PacketWriter packetWriter;

    public PresenceService(SessionRegistry sessionRegistry, ConversationRepository conversationRepository, PacketWriter packetWriter) {
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
        this.conversationRepository = Objects.requireNonNull(conversationRepository, "conversationRepository must not be null");
        this.packetWriter = Objects.requireNonNull(packetWriter, "packetWriter must not be null");
    }

    public void publish(UserDto user, boolean online) {
        for (long relatedUserId : conversationRepository.relatedUserIds(user.id())) {
            for (ClientSession recipient : sessionRegistry.findByUserId(relatedUserId)) {
                try {
                    packetWriter.write(recipient, Packet.of(MessageType.PRESENCE, null, java.util.Map.of(
                            "userId", user.id(), "online", online)));
                } catch (IOException exception) {
                    sessionRegistry.unregister(recipient);
                }
            }
        }
    }

    public void sendKnownPresence(ClientSession session, UserDto user) {
        Set<Long> relatedIds = new HashSet<>();
        for (long id : conversationRepository.relatedUserIds(user.id())) {
            relatedIds.add(id);
        }
        for (long relatedId : relatedIds) {
            if (!sessionRegistry.findByUserId(relatedId).isEmpty()) {
                try {
                    packetWriter.write(session, Packet.of(MessageType.PRESENCE, null, java.util.Map.of(
                            "userId", relatedId, "online", true)));
                } catch (IOException exception) {
                    sessionRegistry.unregister(session);
                    return;
                }
            }
        }
    }

    public void sendPresence(ClientSession session, UserDto user) {
        boolean online = !sessionRegistry.findByUserId(user.id()).isEmpty();
        try {
            packetWriter.write(session, Packet.of(MessageType.PRESENCE, null, java.util.Map.of(
                    "userId", user.id(), "online", online)));
        } catch (IOException exception) {
            sessionRegistry.unregister(session);
        }
    }
}
