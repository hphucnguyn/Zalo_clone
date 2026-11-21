package com.example.zalo.server;

import com.example.zalo.server.config.Database;
import com.example.zalo.server.config.ServerConfig;
import com.example.zalo.server.config.SslContextFactory;
import com.example.zalo.server.net.ChatServer;
import com.example.zalo.server.repository.UserRepository;
import com.example.zalo.server.security.LoginAttemptLimiter;
import com.example.zalo.server.security.PasswordHasher;
import com.example.zalo.server.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts the server infrastructure and TLS accept loop. */
public final class ServerMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMain.class);

    private ServerMain() {
    }

    public static void main(String[] args) {
        try {
            ServerConfig config = ServerConfig.load();
            try (Database database = Database.connect(config);
                 ChatServer chatServer = new ChatServer(
                         config,
                         SslContextFactory.create(config.tls()),
                         new AuthService(
                                 new UserRepository(database.dataSource()),
                                 new PasswordHasher(config.auth().bcryptRounds()),
                                 new LoginAttemptLimiter(config.auth().maxLoginAttempts(), config.auth().lockDuration())))) {
                LOGGER.info("DB connected, schema ready");
                LOGGER.info("TLS context loaded");
                chatServer.start();
                LOGGER.info("TLS chat server listening on {}:{}", config.host(), chatServer.port());
                Runtime.getRuntime().addShutdownHook(new Thread(chatServer::close, "server-shutdown"));
                Thread.currentThread().join();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | java.io.IOException exception) {
            LOGGER.error("Server infrastructure startup failed: {}", exception.getMessage());
            System.exit(1);
        }
    }
}
