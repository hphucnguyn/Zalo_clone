package com.example.zalo.client.cache;

import com.example.zalo.common.model.MessageDto;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageCacheTest {

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
