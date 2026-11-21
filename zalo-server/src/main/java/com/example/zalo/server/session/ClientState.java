package com.example.zalo.server.session;

/** Lifecycle of a client connection on the server. */
public enum ClientState {
    DISCONNECTED,
    TCP_CONNECTED,
    TLS_READY,
    AUTHENTICATED,
    CHATTING,
    CLOSED
}
