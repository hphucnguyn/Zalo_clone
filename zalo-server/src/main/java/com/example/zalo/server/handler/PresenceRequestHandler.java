package com.example.zalo.server.handler;

import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.repository.UserRepository;
import com.example.zalo.server.service.PresenceService;
import com.example.zalo.server.session.ClientSession;

import java.io.IOException;
import java.util.Objects;

/** Returns the server-authoritative presence state for a requested user. */
public final class PresenceRequestHandler implements PacketHandler {

    private final PresenceService presenceService;
    private final UserRepository userRepository;

    public PresenceRequestHandler(PresenceService presenceService, UserRepository userRepository) {
        this.presenceService = Objects.requireNonNull(presenceService, "presenceService must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    @Override
    public void handle(ClientSession session, Packet packet) throws IOException {
        long userId = packet.data().path("userId").asLong(-1);
        if (userId > 0) {
            presenceService.sendPresence(session, userId);
            return;
        }
        String username = packet.data().path("username").asText(null);
        if (username != null && !username.isBlank()) {
            userRepository.findPublicByUsername(username.trim())
                    .ifPresent(user -> presenceService.sendPresence(session, user.id()));
        }
    }
}
