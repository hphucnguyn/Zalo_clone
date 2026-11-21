package com.example.zalo.common.protocol;

/** Stable error identifiers sent in packets of type {@link MessageType#ERROR}. */
public enum ErrorCode {
    INVALID_JSON,
    INVALID_PACKET,
    UNKNOWN_TYPE,
    TOO_LARGE,
    NOT_AUTHENTICATED,
    UNAUTHORIZED,
    VALIDATION_FAILED,
    NOT_FOUND,
    CONFLICT,
    INTERNAL_ERROR
}
