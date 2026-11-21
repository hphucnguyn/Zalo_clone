package com.example.zalo.common.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Message names used on the newline-delimited JSON protocol. */
public enum MessageType {
    REGISTER("register"),
    REGISTER_RESULT("register_result"),
    LOGIN("login"),
    LOGIN_RESULT("login_result"),
    OPEN_DIRECT("open_direct"),
    DIRECT_OPENED("direct_opened"),
    SEND_MESSAGE("send_message"),
    MESSAGE("message"),
    HISTORY_REQUEST("history_request"),
    HISTORY_RESPONSE("history_response"),
    PRESENCE("presence"),
    MARK_SEEN("mark_seen"),
    MESSAGE_STATUS("message_status"),
    PING("ping"),
    PONG("pong"),
    ERROR("error"),
    SYSTEM("system"),
    DISCONNECT("disconnect");

    private final String wireName;

    MessageType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static MessageType fromWireName(String value) {
        return Arrays.stream(values())
                .filter(type -> type.wireName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown message type: " + value));
    }
}
