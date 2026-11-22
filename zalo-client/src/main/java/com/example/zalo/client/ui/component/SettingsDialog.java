package com.example.zalo.client.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Settings modal for Zalo Desktop. */
public final class SettingsDialog {

    private SettingsDialog() {}

    public static void show(Stage owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("Cài đặt Zalo PC");

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setPrefWidth(420);
        root.setStyle("-fx-background-color: white;");

        Label title = new Label("Cài đặt hệ thống");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #111;");

        // General settings
        VBox generalBox = new VBox(10);
        Label section1 = new Label("CÀI ĐẶT CHUNG");
        section1.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888;");

        CheckBox soundCheck = new CheckBox("Phát âm thanh khi có tin nhắn mới");
        soundCheck.setSelected(true);

        CheckBox autoScrollCheck = new CheckBox("Tự động cuộn đến tin nhắn mới nhất");
        autoScrollCheck.setSelected(true);

        CheckBox offlineCacheCheck = new CheckBox("Bật bộ nhớ đệm SQLite offline");
        offlineCacheCheck.setSelected(true);

        generalBox.getChildren().addAll(section1, soundCheck, autoScrollCheck, offlineCacheCheck);

        // Security & Connection
        VBox secBox = new VBox(10);
        Label section2 = new Label("KẾT NỐI & BẢO MẬT");
        section2.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888;");

        Label tlsLabel = new Label("Giao thức mã hóa: TLSv1.3 (SSLSocket)");
        tlsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #444;");

        Label serverLabel = new Label("Server: 127.0.0.1:5000 (Đang hoạt động)");
        serverLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #22c55e;");

        secBox.getChildren().addAll(section2, tlsLabel, serverLabel);

        // About
        VBox aboutBox = new VBox(6);
        Label section3 = new Label("THÔNG TIN PHIÊN BẢN");
        section3.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888;");
        Label versionLabel = new Label("Zalo Clone Desktop v2.0-SNAPSHOT (JavaFX 21 + TLS)");
        versionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        aboutBox.getChildren().addAll(section3, versionLabel);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("Đóng");
        closeBtn.setMaxWidth(Double.MAX_VALUE);
        closeBtn.getStyleClass().add("button-primary");
        closeBtn.setOnAction(e -> dialog.close());

        root.getChildren().addAll(title, generalBox, new Separator(), secBox, new Separator(), aboutBox, spacer, closeBtn);

        Scene scene = new Scene(root);
        java.net.URL css = SettingsDialog.class.getResource("/css/zalo.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }
}
