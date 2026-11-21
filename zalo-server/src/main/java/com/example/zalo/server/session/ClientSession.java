package com.example.zalo.server.session;

import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/** Per-client socket state. Writes must be serialized using {@link #sendLock()}. */
public final class ClientSession implements AutoCloseable {

    private final String id = UUID.randomUUID().toString();
    private final SSLSocket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final ReentrantLock sendLock = new ReentrantLock();
    private final AtomicReference<ClientState> state = new AtomicReference<>(ClientState.TCP_CONNECTED);

    public ClientSession(SSLSocket socket, BufferedReader reader, BufferedWriter writer) {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
    }

    public String id() {
        return id;
    }

    public SSLSocket socket() {
        return socket;
    }

    public BufferedReader reader() {
        return reader;
    }

    public BufferedWriter writer() {
        return writer;
    }

    public ReentrantLock sendLock() {
        return sendLock;
    }

    public ClientState state() {
        return state.get();
    }

    public void setState(ClientState newState) {
        state.set(Objects.requireNonNull(newState, "newState must not be null"));
    }

    @Override
    public void close() {
        if (state.getAndSet(ClientState.CLOSED) != ClientState.CLOSED) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // The connection is already unusable, and cleanup must remain idempotent.
            }
        }
    }
}
