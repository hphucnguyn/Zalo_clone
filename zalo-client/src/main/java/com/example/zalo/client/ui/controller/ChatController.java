package com.example.zalo.client.ui.controller;

import com.example.zalo.client.service.ChatClient;
import com.example.zalo.client.ui.component.MessageCell;
import com.example.zalo.client.util.UiThread;
import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.MessageDto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import java.io.IOException;

/** Controls the active conversation section of the main screen. */
public final class ChatController {

    private final ChatClient chatClient;
    private final ObservableList<MessageDto> visibleMessages = FXCollections.observableArrayList();
    private ConversationDto activeConversation;
    private ListView<MessageDto> messagesView;
    private TextField messageField;
    private Button sendButton;
    private Label conversationLabel;
    private Label statusLabel;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public void bind(
            ListView<MessageDto> messagesView,
            TextField messageField,
            Button sendButton,
            Label conversationLabel,
            Label statusLabel) {
        this.messagesView = messagesView;
        this.messageField = messageField;
        this.sendButton = sendButton;
        this.conversationLabel = conversationLabel;
        this.statusLabel = statusLabel;
        messagesView.setItems(visibleMessages);
        messagesView.setCellFactory(ignored -> new MessageCell());
        sendButton.setDisable(true);
        chatClient.addMessageConsumer(this::receiveMessage);
    }

    public void showConversation(ConversationDto conversation) {
        activeConversation = conversation;
        conversationLabel.setText("Đang chat với " + conversation.counterpart().displayName());
        visibleMessages.setAll(chatClient.messages(conversation.id()));
        sendButton.setDisable(false);
        messageField.requestFocus();
    }

    public void send() {
        if (activeConversation == null || messageField.getText().isBlank()) {
            return;
        }
        try {
            chatClient.sendText(activeConversation.id(), messageField.getText());
            messageField.clear();
        } catch (IOException exception) {
            statusLabel.setText("Không gửi được tin nhắn: mất kết nối server.");
        }
    }

    private void receiveMessage(MessageDto message) {
        if (activeConversation != null && message.conversationId() == activeConversation.id()) {
            UiThread.run(() -> {
                visibleMessages.add(message);
                messagesView.scrollTo(message);
            });
        }
    }
}
