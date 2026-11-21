package com.example.zalo.common.util;

import com.example.zalo.common.protocol.Packet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** JSON conversion for the newline-delimited wire protocol. */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtil() {
    }

    public static String toJson(Packet packet) {
        try {
            String json = MAPPER.writeValueAsString(packet);
            if (json.indexOf('\n') >= 0 || json.indexOf('\r') >= 0) {
                throw new IllegalStateException("Protocol JSON must fit on one line");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize packet", exception);
        }
    }

    public static Packet fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Packet JSON must not be blank");
        }
        if (json.indexOf('\n') >= 0 || json.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Protocol JSON must be exactly one line");
        }
        try {
            return MAPPER.readValue(json, Packet.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse packet JSON", exception);
        }
    }

    public static JsonNode valueToTree(Object value) {
        return MAPPER.valueToTree(value);
    }

    public static JsonNode nullNode() {
        return MAPPER.nullNode();
    }
}
