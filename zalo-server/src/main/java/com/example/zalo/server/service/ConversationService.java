package com.example.zalo.server.service;

import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.util.TimeUtil;
import com.example.zalo.server.repository.ConversationRepository;
import com.example.zalo.server.repository.UserRepository;
import com.example.zalo.server.security.InputValidator;

import java.util.Objects;

/** Opens the unique direct conversation between the authenticated user and a username. */
public final class ConversationService {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;

    public ConversationService(UserRepository userRepository, ConversationRepository conversationRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.conversationRepository = Objects.requireNonNull(conversationRepository, "conversationRepository must not be null");
    }

    public ConversationDto openDirect(UserDto currentUser, String usernameInput) {
        String username = InputValidator.username(usernameInput);
        UserDto counterpart = userRepository.findPublicByUsername(username)
                .orElseThrow(() -> new ConversationNotFoundException("User was not found"));
        if (currentUser.id() == counterpart.id()) {
            throw new IllegalArgumentException("Cannot open a direct conversation with yourself");
        }
        long conversationId = conversationRepository.findOrCreateDirect(currentUser.id(), counterpart.id(), TimeUtil.nowEpochMillis());
        return new ConversationDto(conversationId, "DIRECT", null, counterpart);
    }

    public static final class ConversationNotFoundException extends RuntimeException {
        public ConversationNotFoundException(String message) {
            super(message);
        }
    }
}
