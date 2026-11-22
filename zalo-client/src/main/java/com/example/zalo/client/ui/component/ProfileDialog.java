package com.example.zalo.client.ui.component;

import com.example.zalo.common.model.UserDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Modal dialog showing the user profile and options including Logout. */
public final class ProfileDialog {

    private ProfileDialog() {}

    public static void show(Stage owner, UserDto user, Runnable onLogout) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("Thông tin tài khoản");

        VBox content = new VBox(16);
        content.setPadding(new Insets(24));
        content.setAlignment(Pos.CENTER);
        content.setPrefWidth(340);
        content.setStyle("-fx-background-color: white;");

        // Avatar
        String initial = user.displayName().isEmpty() ? "?" : String.valueOf(user.displayName().charAt(0)).toUpperCase();
        Circle circle = new Circle(36);
        circle.setStyle("-fx-fill: #0068ff;");
        Label initialLabel = new Label(initial);
        initialLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: white;");
        StackPane avatar = new StackPane(circle, initialLabel);

        Label nameLabel = new Label(user.displayName());
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #111;");

        Label usernameLabel = new Label("@" + user.username());
        usernameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666;");

        HBox statusRow = new HBox(6);
        statusRow.setAlignment(Pos.CENTER);
        Circle dot = new Circle(4);
        dot.setStyle("-fx-fill: #22c55e;");
        Label statusText = new Label("Đang hoạt động");
        statusText.setStyle("-fx-font-size: 12px; -fx-text-fill: #22c55e;");
        statusRow.getChildren().addAll(dot, statusText);

        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.setStyle("-fx-background-color: #f0f0f0;");

        VBox infoBox = new VBox(8);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setMaxWidth(Double.MAX_VALUE);
        infoBox.getChildren().addAll(
                new Label("ID người dùng: #" + user.id()),
                new Label("Bảo mật tài khoản: TLS 1.3"),
                new Label("Thiết bị: Zalo PC Desktop Client")
        );
        infoBox.getChildren().forEach(n -> n.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;"));

        Button logoutBtn = new Button("Đăng xuất tài khoản");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-padding: 10; -fx-background-radius: 8; -fx-cursor: hand;");
        logoutBtn.setOnAction(e -> {
            dialog.close();
            if (onLogout != null) {
                onLogout.run();
            }
        });

        Button closeBtn = new Button("Đóng");
        closeBtn.setMaxWidth(Double.MAX_VALUE);
        closeBtn.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; -fx-font-weight: bold; -fx-padding: 8; -fx-background-radius: 8; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> dialog.close());

        content.getChildren().addAll(avatar, nameLabel, usernameLabel, statusRow, separator, infoBox, logoutBtn, closeBtn);

        Scene scene = new Scene(content);
        java.net.URL css = ProfileDialog.class.getResource("/css/zalo.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }
}
