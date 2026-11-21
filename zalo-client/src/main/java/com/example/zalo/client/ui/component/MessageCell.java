package com.example.zalo.client.ui.component;

import com.example.zalo.common.model.MessageDto;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Displays sender, content, and server-assigned sending time for a text message. */
public final class MessageCell extends ListCell<MessageDto> {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    @Override
    protected void updateItem(MessageDto message, boolean empty) {
        super.updateItem(message, empty);
        if (empty || message == null) {
            setGraphic(null);
            setText(null);
            return;
        }
        Label header = new Label(message.senderName() + " · " + TIME_FORMAT.format(Instant.ofEpochMilli(message.sentAt())));
        header.setStyle("-fx-font-size: 11px; -fx-text-fill: #5f6368;");
        Label content = new Label(message.content());
        content.setWrapText(true);
        VBox box = new VBox(3, header, content);
        box.setStyle("-fx-padding: 7px; -fx-background-color: #f1f3f4; -fx-background-radius: 8px;");
        setGraphic(box);
        setText(null);
    }
}
