package com.example.zalo.client.ui.controller;

import com.example.zalo.client.net.TcpClient;
import com.example.zalo.client.service.AuthClient;
import com.example.zalo.client.service.AuthResponse;
import com.example.zalo.client.util.UiThread;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import com.example.zalo.common.model.UserDto;

/** Login screen; connects to server and handles authentication. */
public final class LoginController {

    private final TcpClient tcpClient;
    private final AuthClient authClient;
    private final Consumer<UserDto> authenticatedConsumer;
    private final Runnable onGoToRegister;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;

    public LoginController(
            TcpClient tcpClient,
            AuthClient authClient,
            Consumer<UserDto> authenticatedConsumer,
            Runnable onGoToRegister) {
        this.tcpClient = tcpClient;
        this.authClient = authClient;
        this.authenticatedConsumer = authenticatedConsumer;
        this.onGoToRegister = onGoToRegister;
    }

    public LoginController(
            TcpClient tcpClient,
            AuthClient authClient,
            Consumer<UserDto> authenticatedConsumer) {
        this(tcpClient, authClient, authenticatedConsumer, () -> {});
    }

    @FXML
    private void initialize() {
        setActionsDisabled(true);
        statusLabel.setText("Đang kết nối an toàn tới server...");
        authClient.setDisconnectConsumer(ignored -> UiThread.run(() -> {
            setActionsDisabled(true);
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Mất kết nối với server.");
        }));

        if (tcpClient.state() == com.example.zalo.client.state.ConnectionState.AUTHENTICATED
                || tcpClient.state() == com.example.zalo.client.state.ConnectionState.TLS_READY) {
            setActionsDisabled(false);
            statusLabel.setStyle("-fx-text-fill: #2e7d32;");
            statusLabel.setText("Đã kết nối TLS. Hãy đăng nhập.");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                tcpClient.connect();
            } catch (java.io.IOException exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((ignored, exception) -> UiThread.run(() -> {
            if (exception == null) {
                setActionsDisabled(false);
                statusLabel.setStyle("-fx-text-fill: #2e7d32;");
                statusLabel.setText("Đã kết nối TLS. Hãy đăng nhập.");
            } else {
                setActionsDisabled(true);
                statusLabel.setStyle("-fx-text-fill: #e53935;");
                statusLabel.setText("Không thể kết nối an toàn tới server.");
            }
        }));
    }

    @FXML
    private void login() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Vui lòng nhập tên đăng nhập và mật khẩu.");
            return;
        }

        setActionsDisabled(true);
        statusLabel.setStyle("-fx-text-fill: #3c4043;");
        statusLabel.setText("Đang đăng nhập...");

        authClient.login(username, password).whenComplete((response, exception) -> UiThread.run(() -> {
            if (exception != null) {
                statusLabel.setStyle("-fx-text-fill: #e53935;");
                statusLabel.setText("Lỗi đăng nhập: Kiểm tra kết nối và thử lại.");
                setActionsDisabled(tcpClient.state() == com.example.zalo.client.state.ConnectionState.DISCONNECTED);
                return;
            }
            if (!response.successful()) {
                statusLabel.setStyle("-fx-text-fill: #e53935;");
                statusLabel.setText(response.message());
                setActionsDisabled(false);
                return;
            }
            passwordField.clear();
            authenticatedConsumer.accept(response.user());
            setActionsDisabled(false);
        }));
    }

    @FXML
    private void goToRegister() {
        if (onGoToRegister != null) {
            onGoToRegister.run();
        }
    }

    private void setActionsDisabled(boolean disabled) {
        if (loginButton != null) {
            loginButton.setDisable(disabled);
        }
    }
}
