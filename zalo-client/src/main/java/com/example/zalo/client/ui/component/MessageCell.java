package com.example.zalo.client.ui.component;

import com.example.zalo.common.model.MessageDto;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;
import java.util.function.Function;
import com.example.zalo.common.model.MessageStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Displays sender, content, and server-assigned sending time for a text message. */
public final class MessageCell extends ListCell<MessageDto> {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());
    private final Function<Long, MessageStatus> statusProvider;
    private final long currentUserId;

    public MessageCell(Function<Long, MessageStatus> statusProvider, long currentUserId) {
        this.statusProvider = statusProvider;
        this.currentUserId = currentUserId;
    }

    @Override
    protected void updateItem(MessageDto message, boolean empty) {
        super.updateItem(message, empty);
        if (empty || message == null) {
            setGraphic(null);
            setText(null);
            return;
        }
        MessageStatus msgStatus = statusProvider.apply(message.id());
        String status = message.senderId() == currentUserId ? " · " + statusText(msgStatus) : "";
        String headerColor = (message.senderId() == currentUserId && msgStatus == MessageStatus.SEEN)
                ? statusColor(MessageStatus.SEEN) : "#5f6368";
        Label header = new Label(message.senderName() + " · " + TIME_FORMAT.format(Instant.ofEpochMilli(message.sentAt())) + status);
        header.setStyle("-fx-font-size: 11px; -fx-text-fill: " + headerColor + ";");
        Label content = new Label(message.content());
        content.setWrapText(true);
        VBox box = new VBox(3, header, content);
        box.setStyle("-fx-padding: 7px; -fx-background-color: #f1f3f4; -fx-background-radius: 8px;");
        setGraphic(box);
        setText(null);
    }

    private static String statusText(MessageStatus status) {
        return switch (status) {
            case SENT -> "✓ Đã gửi";
            case DELIVERED -> "✓✓ Đã nhận";
            case SEEN -> "\u2713\u2713 Đã xem";
        };
    }

    private static String statusColor(MessageStatus status) {
        return switch (status) {
            case SENT -> "#5f6368";
            case DELIVERED -> "#5f6368";
            case SEEN -> "#1a73e8";
        };
    }
}
