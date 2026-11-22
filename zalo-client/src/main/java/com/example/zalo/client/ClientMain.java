package com.example.zalo.client;

import com.example.zalo.client.config.ClientConfig;
import com.example.zalo.client.config.TrustStoreLoader;
import com.example.zalo.client.net.TcpClient;
import com.example.zalo.client.service.AuthClient;
import com.example.zalo.client.service.ChatClient;
import com.example.zalo.client.service.FriendClient;
import com.example.zalo.client.cache.MessageCache;
import com.example.zalo.client.ui.controller.LoginController;
import com.example.zalo.client.ui.controller.RegisterController;
import com.example.zalo.client.ui.controller.MainController;
import com.example.zalo.common.model.UserDto;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX entry point for the Zalo Desktop client. */
public final class ClientMain extends Application {

    private TcpClient tcpClient;
    private ClientConfig config;
    private MessageCache messageCache;
    private Stage stage;
    private ChatClient chatClient;
    private AuthClient authClient;
    private FriendClient friendClient;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        config = ClientConfig.load();
        tcpClient = new TcpClient(config, TrustStoreLoader.load(config));
        authClient = new AuthClient(tcpClient);
        chatClient = new ChatClient(tcpClient);
        friendClient = new FriendClient(tcpClient);

        showLoginScreen();
    }

    public void showLoginScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(ClientMain.class.getResource("/fxml/login.fxml"));
            loader.setControllerFactory(type -> {
                if (type == LoginController.class) {
                    return new LoginController(
                            tcpClient,
                            authClient,
                            this::showMainScreen,
                            this::showRegisterScreen);
                }
                throw new IllegalArgumentException("Unsupported FXML controller: " + type.getName());
            });
            Scene scene = new Scene(loader.load());
            applyStylesheet(scene);
            stage.setTitle(config.windowTitle() + " - Đăng nhập");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.show();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not open login screen", exception);
        }
    }

    public void showRegisterScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(ClientMain.class.getResource("/fxml/register.fxml"));
            loader.setControllerFactory(type -> {
                if (type == RegisterController.class) {
                    return new RegisterController(authClient, this::showLoginScreen);
                }
                throw new IllegalArgumentException("Unsupported FXML controller: " + type.getName());
            });
            Scene scene = new Scene(loader.load());
            applyStylesheet(scene);
            stage.setTitle(config.windowTitle() + " - Đăng ký tài khoản");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.show();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not open register screen", exception);
        }
    }

    public void showMainScreen(UserDto user) {
        try {
            if (config.cacheEnabled() && messageCache == null) {
                messageCache = MessageCache.open(
                        config.cacheDirectory(),
                        user.username(),
                        config.cacheMaxMessagesPerConversation());
                chatClient.attachCache(messageCache);
            }
            FXMLLoader loader = new FXMLLoader(ClientMain.class.getResource("/fxml/main.fxml"));
            loader.setControllerFactory(type -> {
                if (type == MainController.class) {
                    return new MainController(chatClient, tcpClient, user, this::logout, friendClient);
                }
                throw new IllegalArgumentException("Unsupported FXML controller: " + type.getName());
            });
            Scene mainScene = new Scene(loader.load());
            applyStylesheet(mainScene);
            stage.setTitle(config.windowTitle() + " - " + user.displayName());
            stage.setScene(mainScene);
            stage.setResizable(true);
            stage.setMinWidth(960);
            stage.setMinHeight(600);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not open chat screen", exception);
        }
    }

    public void logout() {
        if (messageCache != null) {
            try {
                messageCache.close();
            } catch (Exception ignored) {
            }
            messageCache = null;
        }
        showLoginScreen();
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

    private static void applyStylesheet(javafx.scene.Scene scene) {
        java.net.URL css = ClientMain.class.getResource("/css/zalo.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
