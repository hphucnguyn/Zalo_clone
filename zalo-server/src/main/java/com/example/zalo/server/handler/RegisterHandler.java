package com.example.zalo.server.handler;

import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.service.AuthResult;
import com.example.zalo.server.service.AuthService;
import com.example.zalo.server.session.ClientSession;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Handles account creation; the client must still issue a separate login afterwards. */
public final class RegisterHandler implements PacketHandler {

    private final AuthService authService;
    private final PacketWriter packetWriter;

    public RegisterHandler(AuthService authService, PacketWriter packetWriter) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.packetWriter = Objects.requireNonNull(packetWriter, "packetWriter must not be null");
    }

    @Override
    public void handle(ClientSession session, Packet packet) throws IOException {
        AuthResult result = authService.register(
                packet.data().path("username").asText(null),
                packet.data().path("password").asText(null),
                packet.data().path("displayName").asText(null));
        if (result.successful()) {
            packetWriter.write(session, Packet.of(MessageType.REGISTER_RESULT, packet.requestId(), Map.of(
                    "success", true,
                    "user", result.user())));
            return;
        }
        packetWriter.write(session, Packet.of(MessageType.REGISTER_RESULT, packet.requestId(), Map.of(
                "success", false,
                "message", registerMessage(result.failure()))));
    }

    private static String registerMessage(AuthResult.Failure failure) {
        return switch (failure) {
            case USERNAME_UNAVAILABLE -> "Tên đăng nhập đã được sử dụng.";
            default -> "Dữ liệu đăng ký không hợp lệ.";
        };
    }
}
