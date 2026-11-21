package com.example.zalo.server.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory per-username limit for failed login attempts. */
public final class LoginAttemptLimiter {

    private final int maxAttempts;
    private final long lockMillis;
    private final Clock clock;
    private final ConcurrentMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(int maxAttempts, Duration lockDuration) {
        this(maxAttempts, lockDuration, Clock.systemUTC());
    }

    LoginAttemptLimiter(int maxAttempts, Duration lockDuration, Clock clock) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.lockMillis = Objects.requireNonNull(lockDuration, "lockDuration must not be null").toMillis();
        if (lockMillis <= 0) {
            throw new IllegalArgumentException("lockDuration must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public boolean isLocked(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        long now = clock.millis();
        if (attempt.lockedUntilMillis > now) {
            return true;
        }
        if (attempt.lockedUntilMillis > 0) {
            attempts.remove(key, attempt);
        }
        return false;
    }

    /** Records a failure and returns whether this failure locked the account. */
    public boolean recordFailure(String key) {
        long now = clock.millis();
        Attempt updated = attempts.compute(key, (ignored, current) -> {
            if (current == null || (current.lockedUntilMillis > 0 && current.lockedUntilMillis <= now)) {
                current = new Attempt(0, 0);
            }
            int failures = current.failures + 1;
            long lockedUntil = failures >= maxAttempts ? now + lockMillis : 0;
            return new Attempt(failures, lockedUntil);
        });
        return updated.lockedUntilMillis > now;
    }

    public void clear(String key) {
        attempts.remove(key);
    }

    private record Attempt(int failures, long lockedUntilMillis) {
    }
}
