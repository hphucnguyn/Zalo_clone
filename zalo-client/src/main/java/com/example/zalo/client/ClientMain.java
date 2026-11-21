package com.example.zalo.client;

import com.example.zalo.client.config.ClientConfig;
import com.example.zalo.client.config.TrustStoreLoader;
import com.example.zalo.client.net.TcpClient;
import com.example.zalo.client.service.AuthClient;
import com.example.zalo.client.ui.controller.LoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX entry point for the secure login client. */
public final class ClientMain extends Application {

    private TcpClient tcpClient;

    @Override
    public void start(Stage stage) throws Exception {
        ClientConfig config = ClientConfig.load();
        tcpClient = new TcpClient(config, TrustStoreLoader.load(config));
        AuthClient authClient = new AuthClient(tcpClient);

        FXMLLoader loader = new FXMLLoader(ClientMain.class.getResource("/fxml/login.fxml"));
        loader.setControllerFactory(type -> {
            if (type == LoginController.class) {
                return new LoginController(tcpClient, authClient);
            }
            throw new IllegalArgumentException("Unsupported FXML controller: " + type.getName());
        });
        Scene scene = new Scene(loader.load());
        stage.setTitle(config.windowTitle());
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    @Override
    public void stop() {
        if (tcpClient != null) {
            tcpClient.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
