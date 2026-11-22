package com.example.zalo.client.ui.controller;

import com.example.zalo.client.net.TcpClient;
import com.example.zalo.client.service.ChatClient;
import com.example.zalo.client.ui.component.ConversationCell;
import com.example.zalo.client.ui.component.ProfileDialog;
import com.example.zalo.client.ui.component.SettingsDialog;
import com.example.zalo.client.util.UiThread;
import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.MessageDto;
import com.example.zalo.common.model.UserDto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main window controller orchestrating:
 * - Multi-tab views: Chat, Contacts, To-do, My Cloud
 * - Collapsible Right Info Sidebar
 * - Profile and Settings Modals
 * - Conversation List and Message Area
 */
public final class MainController {

    private final ChatClient chatClient;
    private final TcpClient tcpClient;
    private final UserDto currentUser;
    private final Runnable onLogout;
    private final ChatController chatController;

    private final ObservableList<ConversationDto> conversations = FXCollections.observableArrayList();
    private final ObservableList<String> todoItems = FXCollections.observableArrayList();
    private final ObservableList<String> cloudItems = FXCollections.observableArrayList();

    // ── Nav Sidebar ──
    @FXML private StackPane navAvatarBtn;
    @FXML private Label welcomeLabel;
    @FXML private StackPane navChatBtn;
    @FXML private StackPane navContactsBtn;
    @FXML private StackPane navTodoBtn;
    @FXML private StackPane navCloudBtn;
    @FXML private StackPane navSettingsBtn;

    // ── Tab Containers ──
    @FXML private HBox chatViewContainer;
    @FXML private HBox contactsViewContainer;
    @FXML private VBox todoViewContainer;
    @FXML private VBox cloudViewContainer;

    // ── Chat View Controls ──
    @FXML private TextField recipientUsernameField;
    @FXML private Button openDirectButton;
    @FXML private TextField groupNameField;
    @FXML private Button createGroupButton;
    @FXML private Label statusLabel;
    @FXML private Label offlineBanner;
    @FXML private ListView<ConversationDto> conversationsView;

    @FXML private Label topbarAvatarIcon;
    @FXML private Label conversationLabel;
    @FXML private Label presenceLabel;
    @FXML private Button addMemberButton;
    @FXML private HBox addMemberBar;
    @FXML private TextField memberUsernameField;

    @FXML private ListView<MessageDto> messagesView;
    @FXML private Button loadOlderButton;
    @FXML private TextField messageField;
    @FXML private Button sendButton;

    // ── Right Info Panel ──
    @FXML private VBox infoPanel;
    @FXML private Label infoAvatarLabel;
    @FXML private Label infoNameLabel;
    @FXML private Label infoStatusLabel;
    @FXML private VBox infoMembersBox;
    @FXML private VBox infoMembersList;

    // ── Contacts View Controls ──
    @FXML private TextField contactSearchField;
    @FXML private ListView<String> contactsListView;
    @FXML private Button subContactsFriendsBtn;
    @FXML private Button subContactsGroupsBtn;
    @FXML private Button subContactsRequestsBtn;

    // ── Todo View Controls ──
    @FXML private TextField newTodoField;
    @FXML private ListView<String> todoListView;

    // ── Cloud View Controls ──
    @FXML private TextField cloudNoteField;
    @FXML private ListView<String> cloudListView;

    public MainController(ChatClient chatClient, TcpClient tcpClient, UserDto currentUser, Runnable onLogout) {
        this.chatClient = chatClient;
        this.tcpClient = tcpClient;
        this.currentUser = currentUser;
        this.onLogout = onLogout;
        this.chatController = new ChatController(chatClient, currentUser);
    }

    public MainController(ChatClient chatClient, TcpClient tcpClient, UserDto currentUser) {
        this(chatClient, tcpClient, currentUser, () -> {});
    }

    @FXML
    private void initialize() {
        // User avatar initial
        String initial = currentUser.displayName().isEmpty()
                ? "?"
                : String.valueOf(currentUser.displayName().charAt(0)).toUpperCase();
        welcomeLabel.setText(initial);

        // Bind ChatController
        chatController.bind(messagesView, messageField, sendButton, loadOlderButton,
                conversationLabel, statusLabel);
        chatClient.setCurrentUser(currentUser);

        // Setup Conversations ListView
        conversationsView.setItems(conversations);
        conversationsView.setCellFactory(ignored ->
                new ConversationCell(chatClient::messages, chatClient::isOnline));
        conversationsView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                activateConversation(newVal);
            }
        });

        // Offline Banner
        offlineBanner.setManaged(false);
        offlineBanner.setVisible(false);
        tcpClient.addDisconnectConsumer(ignored -> UiThread.run(() -> {
            offlineBanner.setManaged(true);
            offlineBanner.setVisible(true);
            statusLabel.setStyle("-fx-text-fill: #b45309;");
            statusLabel.setText("Đang offline: xem lịch sử đã lưu.");
        }));

        chatClient.addPresenceConsumer(ignored -> UiThread.run(() -> {
            refreshPresence();
            conversationsView.refresh();
        }));

        chatClient.addMessageConsumer(msg -> UiThread.run(() -> {
            conversationsView.refresh();
        }));

        // Setup Contacts ListView
        setupContactsView();

        // Setup Todo ListView
        setupTodoView();

        // Setup Cloud ListView
        setupCloudView();
    }

    // ── Tab Switching ──

    @FXML
    public void navChat() {
        setActiveNav(navChatBtn);
        showContainer(chatViewContainer);
    }

    @FXML
    public void navContacts() {
        setActiveNav(navContactsBtn);
        showContainer(contactsViewContainer);
    }

    @FXML
    public void navTodo() {
        setActiveNav(navTodoBtn);
        showContainer(todoViewContainer);
    }

    @FXML
    public void navCloud() {
        setActiveNav(navCloudBtn);
        showContainer(cloudViewContainer);
    }

    @FXML
    public void openSettings() {
        Stage stage = (Stage) welcomeLabel.getScene().getWindow();
        SettingsDialog.show(stage);
    }

    @FXML
    public void openProfile() {
        Stage stage = (Stage) welcomeLabel.getScene().getWindow();
        ProfileDialog.show(stage, currentUser, onLogout);
    }

    private void setActiveNav(StackPane targetBtn) {
        StackPane[] navBtns = {navChatBtn, navContactsBtn, navTodoBtn, navCloudBtn};
        for (StackPane btn : navBtns) {
            if (btn != null) {
                btn.getStyleClass().remove("active");
            }
        }
        if (targetBtn != null) {
            targetBtn.getStyleClass().add("active");
        }
    }

    private void showContainer(Node activeNode) {
        Node[] nodes = {chatViewContainer, contactsViewContainer, todoViewContainer, cloudViewContainer};
        for (Node node : nodes) {
            if (node != null) {
                boolean match = (node == activeNode);
                node.setVisible(match);
                node.setManaged(match);
            }
        }
    }

    // ── Right Info Panel ──

    @FXML
    public void toggleInfoPanel() {
        boolean isVisible = infoPanel.isVisible();
        infoPanel.setVisible(!isVisible);
        infoPanel.setManaged(!isVisible);
        if (!isVisible) {
            updateInfoPanelData();
        }
    }

    private void updateInfoPanelData() {
        ConversationDto active = chatController.activeConversation();
        if (active == null) {
            infoNameLabel.setText("Chưa chọn hội thoại");
            infoAvatarLabel.setText("?");
            infoStatusLabel.setText("");
            infoMembersBox.setVisible(false);
            infoMembersBox.setManaged(false);
            return;
        }

        boolean isGroup = "GROUP".equalsIgnoreCase(active.type());
        String name = isGroup
                ? (active.name() != null ? active.name() : "Nhóm")
                : (active.counterpart() != null ? active.counterpart().displayName() : "Hội thoại");

        infoNameLabel.setText(name);
        infoAvatarLabel.setText(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());

        if (isGroup) {
            infoStatusLabel.setText("Nhóm trò chuyện");
            infoMembersBox.setVisible(true);
            infoMembersBox.setManaged(true);
            infoMembersList.getChildren().clear();

            // Default group member tags
            addMemberToInfoList(currentUser.displayName() + " (Bạn)", "Trưởng nhóm", "#0068ff");
            if (active.counterpart() != null) {
                addMemberToInfoList(active.counterpart().displayName(), "Thành viên", "#8b5cf6");
            }
        } else {
            boolean online = active.counterpart() != null && chatClient.isOnline(active.counterpart().id());
            infoStatusLabel.setText(online ? "● Đang hoạt động" : "○ Offline");
            infoStatusLabel.setStyle(online ? "-fx-text-fill: #22c55e;" : "-fx-text-fill: #999;");
            infoMembersBox.setVisible(false);
            infoMembersBox.setManaged(false);
        }
    }

    private void addMemberToInfoList(String name, String role, String color) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 4 0;");

        Circle av = new Circle(12);
        av.setStyle("-fx-fill: " + color + ";");
        Label initial = new Label(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());
        initial.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: white;");
        StackPane avPane = new StackPane(av, initial);

        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #333;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label roleLbl = new Label(role);
        roleLbl.setStyle("-fx-font-size: 10px; -fx-background-color: #e8f0fe; -fx-text-fill: #0068ff; -fx-padding: 1 6; -fx-background-radius: 6;");

        row.getChildren().addAll(avPane, nameLbl, spacer, roleLbl);
        infoMembersList.getChildren().add(row);
    }

    // ── Add Member Bar ──

    @FXML
    public void toggleAddMemberBar() {
        boolean visible = addMemberBar.isVisible();
        addMemberBar.setVisible(!visible);
        addMemberBar.setManaged(!visible);
        if (!visible) {
            memberUsernameField.requestFocus();
        }
    }

    // ── Chat Actions ──

    @FXML
    private void openDirect() {
        String username = recipientUsernameField.getText() == null ? "" : recipientUsernameField.getText().trim();
        if (username.isBlank()) {
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Nhập username người bạn muốn chat.");
            return;
        }
        openDirectButton.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: #3c4043;");
        statusLabel.setText("Đang mở hội thoại...");

        chatClient.openDirect(username).whenComplete((conversation, exception) -> UiThread.run(() -> {
            openDirectButton.setDisable(false);
            if (exception != null) {
                statusLabel.setStyle("-fx-text-fill: #e53935;");
                statusLabel.setText("Không thể mở hội thoại. Kiểm tra username.");
                return;
            }
            addOrSelectConversation(conversation);
            recipientUsernameField.clear();
        }));
    }

    @FXML
    private void createGroup() {
        String groupName = groupNameField.getText() == null ? "" : groupNameField.getText().trim();
        if (groupName.isBlank()) {
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Nhập tên nhóm.");
            return;
        }
        createGroupButton.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: #3c4043;");
        statusLabel.setText("Đang tạo nhóm...");

        chatClient.createGroup(groupName).whenComplete((conversation, exception) -> UiThread.run(() -> {
            createGroupButton.setDisable(false);
            if (exception != null) {
                statusLabel.setStyle("-fx-text-fill: #e53935;");
                statusLabel.setText("Không thể tạo nhóm: " + exception.getMessage());
                return;
            }
            addOrSelectConversation(conversation);
            statusLabel.setStyle("-fx-text-fill: #2e7d32;");
            statusLabel.setText("Đã tạo nhóm: " + conversation.name());
            groupNameField.clear();
        }));
    }

    @FXML
    private void addMember() {
        ConversationDto active = chatController.activeConversation();
        String memberUser = memberUsernameField.getText() == null ? "" : memberUsernameField.getText().trim();

        if (active == null || !"GROUP".equals(active.type()) || memberUser.isBlank()) {
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Mở một nhóm rồi nhập username thành viên.");
            return;
        }
        try {
            chatClient.addMember(active.id(), memberUser);
            memberUsernameField.clear();
            addMemberBar.setVisible(false);
            addMemberBar.setManaged(false);
            statusLabel.setStyle("-fx-text-fill: #2e7d32;");
            statusLabel.setText("Đã gửi yêu cầu thêm " + memberUser + " vào nhóm.");
            if (infoPanel.isVisible()) {
                addMemberToInfoList(memberUser, "Thành viên", "#14b8a6");
            }
        } catch (IOException exception) {
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Không thể thêm thành viên khi offline.");
        }
    }

    @FXML private void sendMessage() { chatController.send(); }
    @FXML private void loadOlder() { chatController.loadOlder(); }

    private void addOrSelectConversation(ConversationDto conversation) {
        boolean exists = false;
        for (ConversationDto c : conversations) {
            if (c.id() == conversation.id()) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            conversations.add(0, conversation);
        }
        conversationsView.getSelectionModel().select(conversation);
        activateConversation(conversation);
    }

    private void activateConversation(ConversationDto conversation) {
        chatController.showConversation(conversation);
        refreshPresence();
        statusLabel.setText("");

        boolean isGroup = "GROUP".equalsIgnoreCase(conversation.type());
        topbarAvatarIcon.setText(isGroup ? "👥" : "👤");

        if (infoPanel.isVisible()) {
            updateInfoPanelData();
        }
    }

    private void refreshPresence() {
        ConversationDto active = chatController.activeConversation();
        if (active == null || active.counterpart() == null) {
            presenceLabel.setText("");
            return;
        }
        long counterpartId = active.counterpart().id();
        boolean online = chatClient.isOnline(counterpartId);
        presenceLabel.setStyle(online
                ? "-fx-text-fill: #22c55e; -fx-font-size: 12px;"
                : "-fx-text-fill: #9e9e9e; -fx-font-size: 12px;");
        presenceLabel.setText(online ? "● Đang hoạt động" : "○ Offline");
    }

    // ── Additional Rich Chat Feature Stubs ──

    @FXML
    private void handleCall() {
        showInfoAlert("Cuộc gọi thoại", "Đang khởi tạo cuộc gọi thoại bảo mật P2P qua Zalo...");
    }

    @FXML
    private void handleVideoCall() {
        showInfoAlert("Cuộc gọi video", "Đang kết nối camera và mic để gọi video...");
    }

    @FXML
    private void handlePinMessage() {
        statusLabel.setStyle("-fx-text-fill: #2e7d32;");
        statusLabel.setText("Đã ghim tin nhắn quan trọng vào đầu hội thoại.");
    }

    @FXML
    private void handleInsertEmoji() {
        messageField.setText(messageField.getText() + " 😊 ");
        messageField.positionCaret(messageField.getText().length());
    }

    @FXML
    private void handleSendImage() {
        showInfoAlert("Gửi hình ảnh", "Chọn ảnh từ máy tính để gửi với độ phân giải HD chuẩn Zalo.");
    }

    @FXML
    private void handleSendFile() {
        showInfoAlert("Gửi file", "Hỗ trợ gửi tài liệu, PDF, Word, Excel lên đến 50MB.");
    }

    @FXML
    private void handleScreenshot() {
        showInfoAlert("Chụp ảnh màn hình", "Nhấn phím tắt Alt+A để chụp nhanh ảnh màn hình kèm ghi chú.");
    }

    @FXML
    private void handleReminder() {
        showInfoAlert("Nhắc hẹn", "Tạo nhắc hẹn lịch họp hoặc deadline cho các thành viên trong hội thoại.");
    }

    @FXML
    private void handleQuickLike() {
        ConversationDto active = chatController.activeConversation();
        if (active != null) {
            try {
                chatClient.sendText(active.id(), "👍");
            } catch (IOException e) {
                statusLabel.setText("Không gửi được like.");
            }
        }
    }

    // ── Contacts View ──

    private void setupContactsView() {
        ObservableList<String> sampleContacts = FXCollections.observableArrayList(
                "Trần Văn Minh · @minh_tv (Online)",
                "Nguyễn Thị An · @an_nt (Online)",
                "Lê Thị Hà · @ha_lt (Offline)",
                "Phạm Long · @long_pham (Online)",
                "Hoàng Đức · @duc_hoang (Offline)"
        );
        contactsListView.setItems(sampleContacts);
        contactsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-padding: 10 16; -fx-cursor: hand;");

                Circle av = new Circle(18);
                av.setStyle("-fx-fill: #0068ff;");
                Label avLbl = new Label(item.substring(0, 1));
                avLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
                StackPane avPane = new StackPane(av, avLbl);

                Label nameLbl = new Label(item);
                nameLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Button chatBtn = new Button("Nhắn tin");
                chatBtn.getStyleClass().add("button-primary");
                chatBtn.setStyle("-fx-padding: 4 12; -fx-font-size: 12px;");
                chatBtn.setOnAction(e -> {
                    String username = item.split("@")[1].split(" ")[0].trim();
                    navChat();
                    recipientUsernameField.setText(username);
                    openDirect();
                });

                row.getChildren().addAll(avPane, nameLbl, sp, chatBtn);
                setGraphic(row);
                setText(null);
            }
        });
    }

    @FXML
    private void handleFilterContacts() {
        String q = contactSearchField.getText() == null ? "" : contactSearchField.getText().trim().toLowerCase();
        if (q.isBlank()) {
            setupContactsView();
        } else {
            contactsListView.getItems().removeIf(name -> !name.toLowerCase().contains(q));
        }
    }

    @FXML
    private void openDirectFromContacts() {
        navChat();
        recipientUsernameField.requestFocus();
    }

    // ── Todo View ──

    private void setupTodoView() {
        todoItems.addAll(
                "Hoàn thành giao diện Zalo Desktop PC (Hôm nay)",
                "Kiểm tra chức năng gửi nhận tin nhắn TLS (Ưu tiên cao)",
                "Review code bảo mật mật khẩu BCrypt"
        );
        todoListView.setItems(todoItems);
        todoListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-padding: 10 14;");

                CheckBox cb = new CheckBox();
                Label lbl = new Label(item);
                lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #333;");

                cb.selectedProperty().addListener((obs, oldV, isDone) -> {
                    if (isDone) {
                        lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #999; -fx-strikethrough: true;");
                    } else {
                        lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #333; -fx-strikethrough: false;");
                    }
                });

                row.getChildren().addAll(cb, lbl);
                setGraphic(row);
                setText(null);
            }
        });
    }

    @FXML
    private void handleCreateTodo() {
        String text = newTodoField.getText() == null ? "" : newTodoField.getText().trim();
        if (!text.isBlank()) {
            todoItems.add(0, text + " (Mới)");
            newTodoField.clear();
        }
    }

    // ── Cloud View ──

    private void setupCloudView() {
        cloudItems.addAll(
                "📝 Ghi chú: Kế hoạch sprint Lập trình mạng 2026",
                "📄 File: Tài liệu thuyết trình Zalo Clone.pdf (4.5 MB)",
                "🔗 Link: https://github.com/ZaloClone"
        );
        cloudListView.setItems(cloudItems);
        cloudListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-padding: 12 16;");
                Label lbl = new Label(item);
                lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #333;");
                row.getChildren().add(lbl);
                setGraphic(row);
                setText(null);
            }
        });
    }

    @FXML
    private void handleSaveCloudNote() {
        String note = cloudNoteField.getText() == null ? "" : cloudNoteField.getText().trim();
        if (!note.isBlank()) {
            cloudItems.add(0, "📝 " + note);
            cloudNoteField.clear();
        }
    }

    @FXML
    private void handleUploadCloud() {
        showInfoAlert("Tải lên Cloud", "Kéo thả hoặc duyệt file từ máy tính để lưu trữ vào Cloud cá nhân.");
    }

    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }
}
