package com.example.zalo.server.service;

import com.example.zalo.common.model.UserDto;

/** Result of a registration or login attempt, safe to turn into a client response. */
public record AuthResult(boolean successful, UserDto user, Failure failure) {

    public static AuthResult success(UserDto user) {
        return new AuthResult(true, user, null);
    }

    public static AuthResult failure(Failure failure) {
        return new AuthResult(false, null, failure);
    }

    public enum Failure {
        INVALID_REGISTRATION,
        USERNAME_UNAVAILABLE,
        PHONE_UNAVAILABLE,
        INVALID_CREDENTIALS,
        TEMPORARILY_LOCKED
    }
}
