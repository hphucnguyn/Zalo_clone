package com.example.zalo.common.model;

/** Minimal chat message representation. {@code sentAt} is Unix epoch milliseconds. */
public record MessageDto(
        long id,
        long conversationId,
        long senderId,
        String senderName,
        String content,
        long sentAt) {
}
