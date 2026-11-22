package com.example.zalo.client.ui.component;

import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.MessageDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * Custom ListCell for rendering conversations in Zalo Desktop style:
 * - Avatar with colored background and initials
 * - Online status dot for direct contacts
 * - Title (Contact or Group name)
 * - Last message snippet
 * - Formatted time
 */
public final class ConversationCell extends ListCell<ConversationDto> {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneId.systemDefault());

    private static final String[] PALETTE = {
            "#0068ff", "#8b5cf6", "#ec4899", "#f97316",
            "#14b8a6", "#f59e0b", "#06b6d4", "#10b981"
    };

    private final Function<Long, List<MessageDto>> messagesProvider;
    private final Function<Long, Boolean> presenceProvider;

    public ConversationCell(
            Function<Long, List<MessageDto>> messagesProvider,
            Function<Long, Boolean> presenceProvider) {
        this.messagesProvider = messagesProvider;
        this.presenceProvider = presenceProvider;
        setStyle("-fx-background-color: transparent; -fx-padding: 0;");
    }

    @Override
    protected void updateItem(ConversationDto item, boolean empty) {
        super.updateItem(item, empty);
        setText(null);
        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        boolean isGroup = "GROUP".equalsIgnoreCase(item.type());
        String name = isGroup
                ? (item.name() != null ? item.name() : "Nhóm")
                : (item.counterpart() != null ? item.counterpart().displayName() : "Người dùng");

        String initial = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
        String color = PALETTE[(int) (Math.abs(item.id()) % PALETTE.length)];

        // Avatar
        Circle circle = new Circle(20);
        circle.setStyle("-fx-fill: " + color + ";");
        Label initialLabel = new Label(isGroup ? "👥" : initial);
        initialLabel.setStyle("-fx-font-size: " + (isGroup ? "14px" : "15px") + "; -fx-font-weight: bold; -fx-text-fill: white;");
        StackPane avatarPane = new StackPane(circle, initialLabel);
        avatarPane.setMinSize(42, 42);
        avatarPane.setMaxSize(42, 42);

        // Online indicator for direct conversation
        if (!isGroup && item.counterpart() != null && presenceProvider.apply(item.counterpart().id())) {
            Circle onlineDot = new Circle(5);
            onlineDot.setStyle("-fx-fill: #22c55e; -fx-stroke: white; -fx-stroke-width: 1.5;");
            StackPane.setAlignment(onlineDot, Pos.BOTTOM_RIGHT);
            avatarPane.getChildren().add(onlineDot);
        }

        // Title
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        nameLabel.setMaxWidth(160);

        // Last message & time
        List<MessageDto> msgs = messagesProvider.apply(item.id());
        String snippet = "Chưa có tin nhắn";
        String timeStr = "";

        if (msgs != null && !msgs.isEmpty()) {
            MessageDto last = msgs.get(msgs.size() - 1);
            snippet = (last.senderName() != null ? last.senderName() + ": " : "") + last.content();
            timeStr = formatTime(last.sentAt());
        }

        Label snippetLabel = new Label(snippet);
        snippetLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #777;");
        snippetLabel.setMaxWidth(160);

        Label timeLabel = new Label(timeStr);
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #aaa;");

        VBox textBox = new VBox(2, nameLabel, snippetLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox root = new HBox(10, avatarPane, textBox, spacer, timeLabel);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(10, 12, 10, 12));
        root.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");

        setGraphic(root);
    }

    private static String formatTime(long epochMillis) {
        if (epochMillis <= 0) return "";
        Instant instant = Instant.ofEpochMilli(epochMillis);
        LocalDate msgDate = instant.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();

        if (msgDate.equals(today)) {
            return TIME_FMT.format(instant);
        } else if (msgDate.equals(today.minusDays(1))) {
            return "Hôm qua";
        } else {
            return DATE_FMT.format(instant);
        }
    }
}
