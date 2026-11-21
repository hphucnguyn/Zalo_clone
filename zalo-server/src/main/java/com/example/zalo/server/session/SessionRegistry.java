package com.example.zalo.server.session;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe registry for active, TLS-ready client sessions. */
public final class SessionRegistry {

    private final ConcurrentMap<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong removalCount = new AtomicLong();

    public void register(ClientSession session) {
        Objects.requireNonNull(session, "session must not be null");
        if (sessions.putIfAbsent(session.id(), session) != null) {
            throw new IllegalStateException("Duplicate session id: " + session.id());
        }
    }

    /**
     * Removes and closes a session. Repeated calls are safe and only the first call counts as a removal.
     */
    public boolean unregister(ClientSession session) {
        Objects.requireNonNull(session, "session must not be null");
        boolean removed = sessions.remove(session.id(), session);
        if (removed) {
            removalCount.incrementAndGet();
        }
        session.close();
        return removed;
    }

    public ClientSession find(String sessionId) {
        return sessions.get(sessionId);
    }

    public int size() {
        return sessions.size();
    }

    public long removalCount() {
        return removalCount.get();
    }

    public Collection<ClientSession> snapshot() {
        return List.copyOf(sessions.values());
    }

    public void closeAll() {
        for (ClientSession session : snapshot()) {
            unregister(session);
        }
    }
}
