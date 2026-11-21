package com.example.zalo.client.ui.controller;

import com.example.zalo.client.service.ChatClient;
import com.example.zalo.client.util.UiThread;
import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.MessageDto;
import com.example.zalo.common.model.UserDto;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

/** Main post-login screen for opening and using one-to-one conversations. */
public final class MainController {

    private final ChatClient chatClient;
    private final UserDto currentUser;
    private final ChatController chatController;

    @FXML private Label welcomeLabel;
    @FXML private TextField recipientUsernameField;
    @FXML private Button openDirectButton;
    @FXML private Label statusLabel;
    @FXML private Label conversationLabel;
    @FXML private ListView<MessageDto> messagesView;
    @FXML private TextField messageField;
    @FXML private Button sendButton;

    public MainController(ChatClient chatClient, UserDto currentUser) {
        this.chatClient = chatClient;
        this.currentUser = currentUser;
        this.chatController = new ChatController(chatClient);
    }

    @FXML
    private void initialize() {
        welcomeLabel.setText("Xin chào, " + currentUser.displayName());
        chatController.bind(messagesView, messageField, sendButton, conversationLabel, statusLabel);
    }

    @FXML
    private void openDirect() {
        String username = recipientUsernameField.getText();
        if (username.isBlank()) {
            statusLabel.setText("Nhập username người bạn muốn chat.");
            return;
        }
        openDirectButton.setDisable(true);
        statusLabel.setText("Đang mở hội thoại...");
        chatClient.openDirect(username).whenComplete((conversation, exception) -> UiThread.run(() -> {
            openDirectButton.setDisable(false);
            if (exception != null) {
                statusLabel.setText("Không thể mở hội thoại. Kiểm tra username.");
                return;
            }
            activateConversation(conversation);
        }));
    }

    @FXML
    private void sendMessage() {
        chatController.send();
    }

    private void activateConversation(ConversationDto conversation) {
        chatController.showConversation(conversation);
        statusLabel.setText("Đã mở hội thoại 1-1.");
    }
}
