package com.example.zalo.client.net;

import com.example.zalo.common.protocol.Packet;
import com.example.zalo.common.util.JsonUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/** Serializes writes so concurrent UI actions cannot interleave packet bytes. */
public final class PacketSender {

    private final BufferedWriter writer;
    private final ReentrantLock sendLock = new ReentrantLock();

    public PacketSender(BufferedWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
    }

    public void send(Packet packet) throws IOException {
        sendLock.lock();
        try {
            writer.write(JsonUtil.toJson(packet));
            writer.newLine();
            writer.flush();
        } finally {
            sendLock.unlock();
        }
    }
}
