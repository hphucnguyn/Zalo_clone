package com.example.zalo.client.ui.controller;

import com.example.zalo.client.service.AuthClient;
import com.example.zalo.client.service.AuthResponse;
import com.example.zalo.client.util.UiThread;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller for the dedicated Zalo registration screen (register.fxml).
 */
public final class RegisterController {

    private final AuthClient authClient;
    private final Runnable onGoToLogin;

    @FXML private TextField usernameField;
    @FXML private TextField displayNameField;
    @FXML private TextField phoneNumberField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private CheckBox termsCheckBox;
    @FXML private Button registerButton;
    @FXML private Label statusLabel;

    public RegisterController(AuthClient authClient, Runnable onGoToLogin) {
        this.authClient = authClient;
        this.onGoToLogin = onGoToLogin;
    }

    @FXML
    private void initialize() {
        statusLabel.setText("");
    }

    @FXML
    private void handleRegister() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String displayName = displayNameField.getText() == null ? "" : displayNameField.getText().trim();
        String phoneNumber = phoneNumberField.getText() == null ? "" : phoneNumberField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirmPassword = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (username.isEmpty() || displayName.isEmpty() || phoneNumber.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Vui lòng điền đầy đủ tất cả các trường.");
            return;
        }

        if (!phoneNumber.matches("\\+[1-9][0-9]{7,14}")) {
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Số điện thoại cần ở dạng quốc tế, ví dụ +84901234567.");
            return;
        }

        if (password.length() < 6) {
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Mật khẩu phải có ít nhất 6 ký tự.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Mật khẩu xác nhận không khớp.");
            return;
        }

        if (termsCheckBox != null && !termsCheckBox.isSelected()) {
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Vui lòng đồng ý với Điều khoản dịch vụ.");
            return;
        }

        registerButton.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: #3c4043;");
        statusLabel.setText("Đang đăng ký tài khoản...");

        authClient.register(username, password, displayName, phoneNumber).whenComplete((response, exception) -> UiThread.run(() -> {
            registerButton.setDisable(false);
            if (exception != null) {
                statusLabel.setStyle("-fx-text-fill: #e53935;");
                statusLabel.setText("Lỗi đăng ký: " + exception.getMessage());
                return;
            }
            if (!response.successful()) {
                statusLabel.setStyle("-fx-text-fill: #e53935;");
                statusLabel.setText(response.message());
                return;
            }
            statusLabel.setStyle("-fx-text-fill: #2e7d32;");
            statusLabel.setText("Đăng ký thành công! Đang chuyển sang màn hình Đăng nhập...");
            // Switch to login after a brief moment
            UiThread.runLater(1000, () -> {
                if (onGoToLogin != null) {
                    onGoToLogin.run();
                }
            });
        }));
    }

    @FXML
    private void goToLogin() {
        if (onGoToLogin != null) {
            onGoToLogin.run();
        }
    }
}
