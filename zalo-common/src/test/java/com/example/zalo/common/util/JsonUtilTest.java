package com.example.zalo.common.util;

import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JsonUtilTest {

    @Test
    void packetRoundTripPreservesVietnameseAndEmojiOnOneLine() {
        Packet original = Packet.of(
                MessageType.SEND_MESSAGE,
                "request-123",
                Map.of("conversationId", 42, "content", "Chào bạn 👋! Cà phê nhé? ☕"));

        String json = JsonUtil.toJson(original);
        Packet restored = JsonUtil.fromJson(json);

        assertFalse(json.contains("\n"));
        assertFalse(json.contains("\r"));
        assertEquals(original, restored);
        assertEquals("Chào bạn 👋! Cà phê nhé? ☕", restored.data().get("content").asText());
        assertDoesNotThrow(() -> JsonUtil.fromJson(json));
    }
}
