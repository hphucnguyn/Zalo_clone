package com.example.zalo.client.state;

/** Observable lifecycle used by the client UI. */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    TLS_READY,
    AUTHENTICATED
}
