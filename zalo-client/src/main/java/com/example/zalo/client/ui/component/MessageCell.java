package com.example.zalo.client.ui.component;

import com.example.zalo.common.model.MessageDto;
import com.example.zalo.common.model.MessageStatus;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * Zalo-style message bubble cell.
 *
 * <ul>
 *   <li>Sent messages: right-aligned, light-blue bubble (#dcf1ff)</li>
 *   <li>Received messages: left-aligned, white bubble with avatar circle</li>
 *   <li>Footer shows time and, for sent messages, the delivery status tick</li>
 * </ul>
 */
public final class MessageCell extends ListCell<MessageDto> {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    // Avatar colours by sender-id hash (simple palette)
    private static final String[] AVATAR_COLORS = {
        "#0068ff", "#f97316", "#8b5cf6", "#ec4899",
        "#14b8a6", "#f59e0b", "#06b6d4", "#10b981"
    };

    private final Function<Long, MessageStatus> statusProvider;
    private final long currentUserId;

    public MessageCell(Function<Long, MessageStatus> statusProvider, long currentUserId) {
        this.statusProvider  = statusProvider;
        this.currentUserId   = currentUserId;
        // Remove default list-cell chrome
        setStyle("-fx-background-color: transparent; -fx-padding: 2 16;");
    }

    @Override
    protected void updateItem(MessageDto msg, boolean empty) {
        super.updateItem(msg, empty);
        setText(null);
        if (empty || msg == null) {
            setGraphic(null);
            return;
        }

        boolean isSent = msg.senderId() == currentUserId;
        setGraphic(isSent ? buildSentRow(msg) : buildReceivedRow(msg));
    }

    // ── Sent bubble (right-aligned) ──────────────────────────────────────────

    private HBox buildSentRow(MessageDto msg) {
        MessageStatus status = statusProvider.apply(msg.id());

        // Bubble
        Label content = new Label(msg.content());
        content.setWrapText(true);
        content.setMaxWidth(380);
        content.setStyle("-fx-font-size: 14px; -fx-text-fill: #111;");

        // Footer: time + status
        String statusTick = switch (status) {
            case SENT      -> "✓";
            case DELIVERED -> "✓✓";
            case SEEN      -> "✓✓";
        };
        String tickColor = (status == MessageStatus.SEEN) ? "#0068ff" : "#90a4ae";
        Label footer = new Label(TIME_FMT.format(Instant.ofEpochMilli(msg.sentAt())) + "  " + statusTick);
        footer.setStyle("-fx-font-size: 10px; -fx-text-fill: " + tickColor + ";");
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox bubble = new VBox(4, content, footer);
        bubble.setStyle("""
            -fx-background-color: #dcf1ff;
            -fx-background-radius: 16 4 16 16;
            -fx-padding: 9 13;
            """);
        bubble.setMaxWidth(400);

        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_RIGHT);
        HBox.setMargin(bubble, new Insets(2, 0, 2, 60));
        return row;
    }

    // ── Received bubble (left-aligned with avatar) ───────────────────────────

    private HBox buildReceivedRow(MessageDto msg) {
        // Mini avatar circle
        String avatarColor = AVATAR_COLORS[(int) (Math.abs(msg.senderId()) % AVATAR_COLORS.length)];
        String initial = msg.senderName().isEmpty()
                ? "?"
                : String.valueOf(msg.senderName().charAt(0)).toUpperCase();

        Circle circle = new Circle(16);
        circle.setStyle("-fx-fill: " + avatarColor + ";");
        Label initLabel = new Label(initial);
        initLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: white;");
        StackPane avatar = new StackPane(circle, initLabel);
        avatar.setMinSize(32, 32);
        avatar.setMaxSize(32, 32);

        // Sender name + content
        Label senderName = new Label(msg.senderName());
        senderName.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + avatarColor + ";");

        Label content = new Label(msg.content());
        content.setWrapText(true);
        content.setMaxWidth(380);
        content.setStyle("-fx-font-size: 14px; -fx-text-fill: #222;");

        Label footer = new Label(TIME_FMT.format(Instant.ofEpochMilli(msg.sentAt())));
        footer.setStyle("-fx-font-size: 10px; -fx-text-fill: #aaa;");

        VBox bubble = new VBox(3, senderName, content, footer);
        bubble.setStyle("""
            -fx-background-color: white;
            -fx-background-radius: 4 16 16 16;
            -fx-padding: 9 13;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);
            """);
        bubble.setMaxWidth(400);

        HBox row = new HBox(10, avatar, bubble);
        row.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setMargin(bubble, new Insets(2, 60, 2, 0));
        return row;
    }
}
