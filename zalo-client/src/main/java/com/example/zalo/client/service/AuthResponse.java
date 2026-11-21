package com.example.zalo.client.service;

import com.example.zalo.common.model.UserDto;

/** Result displayed by the login screen. */
public record AuthResponse(boolean successful, String message, UserDto user) {
}
