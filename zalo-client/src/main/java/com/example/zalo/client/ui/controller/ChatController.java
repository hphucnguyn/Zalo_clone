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
import com.example.zalo.common.model.UserDto;

/** Controls the active conversation section of the main screen. */
public final class ChatController {

    private final ChatClient chatClient;
    private final ObservableList<MessageDto> visibleMessages = FXCollections.observableArrayList();
    private volatile ConversationDto activeConversation;
    private ListView<MessageDto> messagesView;
    private TextField messageField;
    private Button sendButton;
    private Button loadOlderButton;
    private Label conversationLabel;
    private Label statusLabel;

    private final UserDto currentUser;

    public ChatController(ChatClient chatClient, UserDto currentUser) {
        this.chatClient = chatClient;
        this.currentUser = currentUser;
    }

    public void bind(
            ListView<MessageDto> messagesView,
            TextField messageField,
            Button sendButton,
            Button loadOlderButton,
            Label conversationLabel,
            Label statusLabel) {
        this.messagesView = messagesView;
        this.messageField = messageField;
        this.sendButton = sendButton;
        this.loadOlderButton = loadOlderButton;
        this.conversationLabel = conversationLabel;
        this.statusLabel = statusLabel;
        messagesView.setItems(visibleMessages);
        messagesView.setCellFactory(ignored -> new MessageCell(chatClient::status, currentUser.id()));
        sendButton.setDisable(true);
        loadOlderButton.setDisable(true);
        chatClient.addMessageConsumer(this::receiveMessage);
        chatClient.addStatusConsumer(ignored -> UiThread.run(messagesView::refresh));
        chatClient.addPresenceConsumer(ignored -> UiThread.run(this::refreshConversationHeader));
    }

    public void showConversation(ConversationDto conversation) {
        activeConversation = conversation;
        refreshConversationHeader();
        if (conversation.counterpart() != null) {
            chatClient.requestPresence(
                    conversation.counterpart().id(),
                    conversation.counterpart().username());
        }
        visibleMessages.clear();
        sendButton.setDisable(false);
        loadOlderButton.setDisable(true);
        chatClient.loadCachedMessages(conversation.id(), 50).whenComplete((messages, exception) -> UiThread.run(() -> {
            if (!isActive(conversation)) {
                return;
            }
            refreshVisibleMessages();
            chatClient.syncNewMessages(conversation.id(), 50).whenComplete((page, syncError) -> UiThread.run(() -> {
                if (!isActive(conversation)) {
                    return;
                }
                if (syncError == null) {
                    refreshVisibleMessages();
                    loadOlderButton.setDisable(!page.hasMore());
                } else {
                    statusLabel.setText("Đang offline: hiển thị lịch sử đã lưu.");
                }
            }));
        }));
        messageField.requestFocus();
        chatClient.messages(conversation.id()).forEach(chatClient::markSeen);
    }

    public void loadOlder() {
        if (activeConversation == null || visibleMessages.isEmpty()) {
            return;
        }
        long beforeId = visibleMessages.getFirst().id();
        loadOlderButton.setDisable(true);
        chatClient.requestHistory(activeConversation.id(), beforeId, 0, 50).whenComplete((page, error) -> UiThread.run(() -> {
            if (activeConversation == null || error != null) {
                statusLabel.setText("Không thể tải thêm lịch sử.");
                return;
            }
            refreshVisibleMessages();
            loadOlderButton.setDisable(!page.hasMore());
        }));
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
                chatClient.markSeen(message);
            });
        }
    }

    public ConversationDto activeConversation() {
        return activeConversation;
    }

    private boolean isActive(ConversationDto conversation) {
        return activeConversation != null && activeConversation.id() == conversation.id();
    }

    private void refreshVisibleMessages() {
        if (activeConversation == null) {
            return;
        }
        visibleMessages.setAll(chatClient.messages(activeConversation.id()));
        if (!visibleMessages.isEmpty()) {
            messagesView.scrollTo(visibleMessages.get(visibleMessages.size() - 1));
        }
    }

    private void refreshConversationHeader() {
        if (activeConversation == null) return;
        if ("GROUP".equals(activeConversation.type())) {
            conversationLabel.setText("Nhóm: " + activeConversation.name());
            return;
        }
        boolean online = chatClient.isOnline(activeConversation.counterpart().id());
        conversationLabel.setText((online ? "● " : "○ ") + "Đang chat với " + activeConversation.counterpart().displayName());
    }
}
