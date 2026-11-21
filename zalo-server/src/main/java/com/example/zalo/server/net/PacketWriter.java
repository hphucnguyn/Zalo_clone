package com.example.zalo.server.net;

import com.example.zalo.common.protocol.Packet;
import com.example.zalo.common.util.JsonUtil;
import com.example.zalo.server.session.ClientSession;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

/** Writes exactly one JSON line at a time, preventing concurrent byte interleaving. */
public final class PacketWriter {

    public void write(ClientSession session, Packet packet) throws IOException {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(packet, "packet must not be null");

        Lock lock = session.sendLock();
        lock.lock();
        try {
            session.writer().write(JsonUtil.toJson(packet));
            session.writer().newLine();
            session.writer().flush();
        } finally {
            lock.unlock();
        }
    }
}
