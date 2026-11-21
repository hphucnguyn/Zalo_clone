package com.example.zalo.client.net;

import com.example.zalo.common.protocol.Packet;
import com.example.zalo.common.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/** Blocking reader that runs outside the JavaFX application thread. */
public final class ReceiveLoop implements Runnable {

    private final BufferedReader reader;
    private final Consumer<Packet> packetConsumer;
    private final Consumer<Throwable> closedConsumer;

    public ReceiveLoop(BufferedReader reader, Consumer<Packet> packetConsumer, Consumer<Throwable> closedConsumer) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
        this.packetConsumer = Objects.requireNonNull(packetConsumer, "packetConsumer must not be null");
        this.closedConsumer = Objects.requireNonNull(closedConsumer, "closedConsumer must not be null");
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                packetConsumer.accept(JsonUtil.fromJson(line));
            }
            closedConsumer.accept(new IOException("Server closed the connection"));
        } catch (Throwable exception) {
            closedConsumer.accept(exception);
        }
    }
}
