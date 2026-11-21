package com.example.zalo.server;

import com.example.zalo.server.config.Database;
import com.example.zalo.server.config.ServerConfig;
import com.example.zalo.server.config.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts and verifies the server infrastructure; network accepting is added in the next step. */
public final class ServerMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMain.class);

    private ServerMain() {
    }

    public static void main(String[] args) {
        try {
            ServerConfig config = ServerConfig.load();
            try (Database ignored = Database.connect(config)) {
                LOGGER.info("DB connected, schema ready");
                SslContextFactory.create(config.tls());
                LOGGER.info("TLS context loaded");
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Server infrastructure startup failed: {}", exception.getMessage());
            System.exit(1);
        }
    }
}
