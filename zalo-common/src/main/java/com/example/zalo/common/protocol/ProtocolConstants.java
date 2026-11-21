package com.example.zalo.common.protocol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Constants shared by the client and server TCP protocol. */
public final class ProtocolConstants {

    public static final int DEFAULT_PORT = 5000;
    public static final int MAX_LINE_BYTES = 64 * 1024;
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    private ProtocolConstants() {
    }
}
