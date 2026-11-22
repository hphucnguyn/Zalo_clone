package com.example.zalo.client.cache;

import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.MessageDto;
import com.example.zalo.common.model.UserDto;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageCacheTest {

    @Test
    void persistsDirectConversationCounterpartIdentity() throws Exception {
        Path directory = Files.createTempDirectory("zalo-cache-conversation-test-");
        ConversationDto conversation = new ConversationDto(
                7,
                "DIRECT",
                null,
                new UserDto(42, "abc", "ABC", null, true));

        try (MessageCache cache = MessageCache.open(directory, "an", 100)) {
            cache.saveConversation(conversation).get(3, TimeUnit.SECONDS);
        }

        try (MessageCache reopened = MessageCache.open(directory, "an", 100)) {
            List<ConversationDto> conversations =
                    reopened.loadConversations().get(3, TimeUnit.SECONDS);
            assertEquals(1, conversations.size());
            assertEquals(42L, conversations.get(0).counterpart().id());
            assertEquals("abc", conversations.get(0).counterpart().username());
            assertEquals("ABC", conversations.get(0).counterpart().displayName());
        }
    }

    @Test
    void persistsHistoryAcrossReopenAndDoesNotDuplicateSyncedMessages() throws Exception {
        Path directory = Files.createTempDirectory("zalo-cache-test-");
        List<MessageDto> messages = List.of(
                new MessageDto(10, 7, 1, "An", "Tin đầu", 1_000),
                new MessageDto(11, 7, 2, "Bình", "Tin sau", 2_000));

        try (MessageCache first = MessageCache.open(directory, "an", 100)) {
            first.saveMessages(messages).get(3, TimeUnit.SECONDS);
            first.saveMessages(messages).get(3, TimeUnit.SECONDS);
            assertEquals(11, first.lastMessageId(7).get(3, TimeUnit.SECONDS));
        }

        try (MessageCache reopened = MessageCache.open(directory, "an", 100)) {
            List<MessageDto> restored = reopened.loadRecent(7, 50).get(3, TimeUnit.SECONDS);
            assertEquals(messages, restored);
        }
    }
}
