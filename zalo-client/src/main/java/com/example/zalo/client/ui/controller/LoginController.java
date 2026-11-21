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
import java.util.function.Supplier;

/** Login and registration screen; all network operations run away from the FX thread. */
public final class LoginController {

    private final TcpClient tcpClient;
    private final AuthClient authClient;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField displayNameField;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Label statusLabel;

    public LoginController(TcpClient tcpClient, AuthClient authClient) {
        this.tcpClient = tcpClient;
        this.authClient = authClient;
    }

    @FXML
    private void initialize() {
        setActionsDisabled(true);
        statusLabel.setText("Đang kết nối an toàn tới server...");
        authClient.setDisconnectConsumer(ignored -> UiThread.run(() -> {
            setActionsDisabled(true);
            statusLabel.setText("Mất kết nối với server.");
        }));
        CompletableFuture.runAsync(() -> {
            try {
                tcpClient.connect();
            } catch (java.io.IOException exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((ignored, exception) -> UiThread.run(() -> {
            if (exception == null) {
                setActionsDisabled(false);
                statusLabel.setText("Đã kết nối TLS. Hãy đăng nhập hoặc đăng ký.");
            } else {
                setActionsDisabled(true);
                statusLabel.setText("Không thể kết nối an toàn tới server.");
            }
        }));
    }

    @FXML
    private void register() {
        submit(() -> authClient.register(usernameField.getText(), passwordField.getText(), displayNameField.getText()), false);
    }

    @FXML
    private void login() {
        submit(() -> authClient.login(usernameField.getText(), passwordField.getText()), true);
    }

    private void submit(Supplier<CompletableFuture<AuthResponse>> request, boolean isLogin) {
        if (usernameField.getText().isBlank() || passwordField.getText().isBlank()
                || (!isLogin && displayNameField.getText().isBlank())) {
            statusLabel.setText("Vui lòng nhập đầy đủ thông tin.");
            return;
        }
        setActionsDisabled(true);
        statusLabel.setText(isLogin ? "Đang đăng nhập..." : "Đang đăng ký...");
        request.get().whenComplete((response, exception) -> UiThread.run(() -> {
            if (exception != null) {
                statusLabel.setText("Yêu cầu không hoàn thành. Kiểm tra kết nối và thử lại.");
                setActionsDisabled(tcpClient.state() == com.example.zalo.client.state.ConnectionState.DISCONNECTED);
                return;
            }
            handleResponse(response, isLogin);
        }));
    }

    private void handleResponse(AuthResponse response, boolean isLogin) {
        if (!response.successful()) {
            statusLabel.setText(response.message());
            setActionsDisabled(false);
            return;
        }
        passwordField.clear();
        if (isLogin) {
            statusLabel.setText("Đăng nhập thành công. Chức năng chat sẽ được mở ở bước tiếp theo.");
        } else {
            statusLabel.setText("Đăng ký thành công. Bây giờ hãy đăng nhập.");
        }
        setActionsDisabled(false);
    }

    private void setActionsDisabled(boolean disabled) {
        loginButton.setDisable(disabled);
        registerButton.setDisable(disabled);
    }
}
