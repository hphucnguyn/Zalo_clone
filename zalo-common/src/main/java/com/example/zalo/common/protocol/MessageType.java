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
    SEARCH_USER_BY_PHONE("search_user_by_phone"),
    USER_SEARCH_RESULT("user_search_result"),
    SEND_FRIEND_REQUEST("send_friend_request"),
    FRIEND_REQUEST_RESULT("friend_request_result"),
    LIST_FRIENDS("list_friends"),
    FRIENDS_LIST("friends_list"),
    LIST_FRIEND_REQUESTS("list_friend_requests"),
    FRIEND_REQUESTS("friend_requests"),
    RESPOND_FRIEND_REQUEST("respond_friend_request"),
    FRIEND_REQUEST_UPDATE("friend_request_update"),
    CREATE_GROUP("create_group"),
    GROUP_CREATED("group_created"),
    ADD_MEMBER("add_member"),
    REMOVE_MEMBER("remove_member"),
    LEAVE_GROUP("leave_group"),
    CONVERSATION_UPDATED("conversation_updated"),
    SEND_MESSAGE("send_message"),
    MESSAGE("message"),
    HISTORY_REQUEST("history_request"),
    HISTORY_RESPONSE("history_response"),
    PRESENCE("presence"),
    PRESENCE_REQUEST("presence_request"),
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
