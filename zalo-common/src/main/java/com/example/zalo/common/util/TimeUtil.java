package com.example.zalo.common.util;

import java.time.Clock;

/** Time helpers; timestamps on the protocol are Unix epoch milliseconds in UTC. */
public final class TimeUtil {

    private TimeUtil() {
    }

    public static long nowEpochMillis() {
        return Clock.systemUTC().millis();
    }
}
