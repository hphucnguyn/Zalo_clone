package com.example.zalo.server.service;

import com.example.zalo.common.model.MessageDto;
import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.util.TimeUtil;
import com.example.zalo.server.repository.ConversationRepository;
import com.example.zalo.server.repository.MessageRepository;

import java.util.Objects;

/** Saves a message before it can be broadcast to any online session. */
public final class MessageService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public MessageService(ConversationRepository conversationRepository, MessageRepository messageRepository) {
        this.conversationRepository = Objects.requireNonNull(conversationRepository, "conversationRepository must not be null");
        this.messageRepository = Objects.requireNonNull(messageRepository, "messageRepository must not be null");
    }

    public SavedMessage sendText(UserDto sender, long conversationId, String contentInput) {
        if (!conversationRepository.isMember(conversationId, sender.id())) {
            throw new UnauthorizedConversationException();
        }
        String content = validateContent(contentInput);
        long sentAt = TimeUtil.nowEpochMillis();
        long messageId = messageRepository.insertText(conversationId, sender.id(), content, sentAt);
        long[] recipients = conversationRepository.memberIds(conversationId);
        MessageDto message = new MessageDto(messageId, conversationId, sender.id(), sender.displayName(), content, sentAt);
        return new SavedMessage(message, recipients);
    }

    private static String validateContent(String contentInput) {
        if (contentInput == null) {
            throw new IllegalArgumentException("Message content is required");
        }
        String content = contentInput.trim();
        if (content.isEmpty() || content.length() > 4_000) {
            throw new IllegalArgumentException("Message must contain 1-4000 characters");
        }
        return content;
    }

    public record SavedMessage(MessageDto message, long[] recipientIds) {
    }

    public static final class UnauthorizedConversationException extends RuntimeException {
        public UnauthorizedConversationException() {
            super("User is not a member of this conversation");
        }
    }
}
