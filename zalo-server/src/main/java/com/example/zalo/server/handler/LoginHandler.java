package com.example.zalo.server.handler;

import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.service.AuthResult;
import com.example.zalo.server.service.AuthService;
import com.example.zalo.server.session.ClientSession;
import com.example.zalo.server.service.PresenceService;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Handles login and records the authenticated user in the client session. */
public final class LoginHandler implements PacketHandler {

    private final AuthService authService;
    private final PacketWriter packetWriter;
    private final PresenceService presenceService;

    public LoginHandler(AuthService authService, PacketWriter packetWriter) {
        this(authService, packetWriter, null);
    }

    public LoginHandler(AuthService authService, PacketWriter packetWriter, PresenceService presenceService) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.packetWriter = Objects.requireNonNull(packetWriter, "packetWriter must not be null");
        this.presenceService = presenceService;
    }

    @Override
    public void handle(ClientSession session, Packet packet) throws IOException {
        AuthResult result = authService.login(
                packet.data().path("username").asText(null),
                packet.data().path("password").asText(null));
        if (result.successful()) {
            session.authenticate(result.user());
            packetWriter.write(session, Packet.of(MessageType.LOGIN_RESULT, packet.requestId(), Map.of(
                    "success", true,
                    "user", result.user())));
            if (presenceService != null) {
                presenceService.sendKnownPresence(session, result.user());
                presenceService.publish(result.user(), true);
            }
            return;
        }
        packetWriter.write(session, Packet.of(MessageType.LOGIN_RESULT, packet.requestId(), Map.of(
                "success", false,
                "message", loginMessage(result.failure()))));
    }

    private static String loginMessage(AuthResult.Failure failure) {
        return failure == AuthResult.Failure.TEMPORARILY_LOCKED
                ? "Tài khoản tạm thời bị khóa. Vui lòng thử lại sau."
                : "Tên đăng nhập hoặc mật khẩu không đúng.";
    }
}
