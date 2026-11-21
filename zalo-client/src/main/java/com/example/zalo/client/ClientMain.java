package com.example.zalo.client;

import com.example.zalo.client.config.ClientConfig;
import com.example.zalo.client.config.TrustStoreLoader;
import com.example.zalo.client.net.TcpClient;
import com.example.zalo.client.service.AuthClient;
import com.example.zalo.client.service.ChatClient;
import com.example.zalo.client.cache.MessageCache;
import com.example.zalo.client.ui.controller.LoginController;
import com.example.zalo.client.ui.controller.MainController;
import com.example.zalo.common.model.UserDto;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX entry point for the secure login client. */
public final class ClientMain extends Application {

    private TcpClient tcpClient;
    private ClientConfig config;
    private MessageCache messageCache;
    private Stage stage;
    private ChatClient chatClient;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        config = ClientConfig.load();
        tcpClient = new TcpClient(config, TrustStoreLoader.load(config));
        AuthClient authClient = new AuthClient(tcpClient);
        chatClient = new ChatClient(tcpClient);

        FXMLLoader loader = new FXMLLoader(ClientMain.class.getResource("/fxml/login.fxml"));
        loader.setControllerFactory(type -> {
            if (type == LoginController.class) {
                return new LoginController(tcpClient, authClient, this::showMainScreen);
            }
            throw new IllegalArgumentException("Unsupported FXML controller: " + type.getName());
        });
        Scene scene = new Scene(loader.load());
        stage.setTitle(config.windowTitle());
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private void showMainScreen(UserDto user) {
        try {
            if (config.cacheEnabled() && messageCache == null) {
                messageCache = MessageCache.open(config.cacheDirectory(), user.username(), config.cacheMaxMessagesPerConversation());
                chatClient.attachCache(messageCache);
            }
            FXMLLoader loader = new FXMLLoader(ClientMain.class.getResource("/fxml/main.fxml"));
            loader.setControllerFactory(type -> {
                if (type == MainController.class) {
                    return new MainController(chatClient, tcpClient, user);
                }
                throw new IllegalArgumentException("Unsupported FXML controller: " + type.getName());
            });
            stage.setScene(new Scene(loader.load()));
            stage.setResizable(true);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not open chat screen", exception);
        }
    }

    @Override
    public void stop() {
        if (tcpClient != null) {
            tcpClient.close();
        }
        if (messageCache != null) {
            messageCache.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
