package com.example.zalo.common.model;

/** A conversation opened by the client. For a direct conversation, {@code counterpart} is the other user. */
public record ConversationDto(long id, String type, String name, UserDto counterpart) {
}
