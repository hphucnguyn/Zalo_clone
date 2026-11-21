package com.example.zalo.common.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.example.zalo.common.util.JsonUtil;

import java.util.Objects;

/** A single newline-delimited protocol message. */
public record Packet(MessageType type, String requestId, JsonNode data) {

    @JsonCreator
    public Packet(
            @JsonProperty("type") MessageType type,
            @JsonProperty("requestId") String requestId,
            @JsonProperty("data") JsonNode data) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.requestId = requestId;
        this.data = data == null ? JsonUtil.nullNode() : data;
    }

    public static Packet of(MessageType type, String requestId, Object data) {
        return new Packet(type, requestId, JsonUtil.valueToTree(data));
    }
}
