package com.example.zalo.common.model;

/** Minimal public user representation safe to send to clients. */
public record UserDto(long id, String username, String displayName, String avatarUrl, boolean online) {
}
