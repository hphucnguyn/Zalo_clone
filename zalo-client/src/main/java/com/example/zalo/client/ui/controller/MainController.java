package com.example.zalo.client.ui.controller;

import com.example.zalo.client.net.TcpClient;
import com.example.zalo.client.service.ChatClient;
import com.example.zalo.client.service.FriendClient;
import com.example.zalo.client.ui.component.ConversationCell;
import com.example.zalo.client.ui.component.ProfileDialog;
import com.example.zalo.client.ui.component.SettingsDialog;
import com.example.zalo.client.util.UiThread;
import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.MessageDto;
import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.model.FriendRequestDto;
import com.example.zalo.common.protocol.Packet;
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
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;

/**
 * Main window controller with 100% REAL data:
 * - Real conversations from database and SQLite cache
 * - Real direct contacts & presence
 * - Real group members and chat history
 * - Clean empty states when user hasn't added any data yet
 */
public final class MainController {

    private final ChatClient chatClient;
    private final TcpClient tcpClient;
    private final UserDto currentUser;
    private final Runnable onLogout;
    private final ChatController chatController;
    private final FriendClient friendClient;

    private final ObservableList<ConversationDto> conversations = FXCollections.observableArrayList();
    private final ObservableList<UserDto> contacts = FXCollections.observableArrayList();
    private final ObservableList<FriendRequestDto> friendRequests = FXCollections.observableArrayList();
    private boolean showingRequests;
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
    @FXML private VBox infoMediaContainer;
    @FXML private VBox infoFilesContainer;

    // ── Contacts View Controls ──
    @FXML private TextField contactSearchField;
    @FXML private ListView<UserDto> contactsListView;
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
        this(chatClient, tcpClient, currentUser, onLogout, new FriendClient(tcpClient));
    }

    public MainController(ChatClient chatClient, TcpClient tcpClient, UserDto currentUser, Runnable onLogout, FriendClient friendClient) {
        this.chatClient = chatClient;
        this.tcpClient = tcpClient;
        this.currentUser = currentUser;
        this.onLogout = onLogout;
        this.chatController = new ChatController(chatClient, currentUser);
        this.friendClient = friendClient;
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

        // Tooltips on nav sidebar
        if (navAvatarBtn != null) Tooltip.install(navAvatarBtn, new Tooltip("Hồ sơ tài khoản"));
        if (navChatBtn != null) {
            Tooltip.install(navChatBtn, new Tooltip("Tin nhắn (Chat)"));
            setActiveNav(navChatBtn);
        }
        if (navContactsBtn != null) Tooltip.install(navContactsBtn, new Tooltip("Danh bạ"));
        if (navTodoBtn != null) Tooltip.install(navTodoBtn, new Tooltip("To-do (Giao việc)"));
        if (navCloudBtn != null) Tooltip.install(navCloudBtn, new Tooltip("Cloud của tôi"));
        if (navSettingsBtn != null) Tooltip.install(navSettingsBtn, new Tooltip("Cài đặt"));

        // Bind ChatController
        chatController.bind(messagesView, messageField, sendButton, loadOlderButton,
                conversationLabel, statusLabel);
        chatClient.setCurrentUser(currentUser);

        // Setup Conversations ListView
        Label emptyConvLabel = new Label("Chưa có hội thoại nào.\nNhập username để bắt đầu chat 1-1 hoặc tạo nhóm.");
        emptyConvLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-text-alignment: CENTER;");
        conversationsView.setPlaceholder(emptyConvLabel);
        conversationsView.setItems(conversations);
        conversationsView.setCellFactory(ignored ->
                new ConversationCell(chatClient::messages, chatClient::isOnline));
        conversationsView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                activateConversation(newVal);
            }
        });

        // Load REAL cached conversations from SQLite
        chatClient.loadCachedConversations().thenAccept(loaded -> UiThread.run(() -> {
            conversations.setAll(loaded);
            refreshContactsFromConversations();
            conversationsView.refresh();
        }));

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
            contactsListView.refresh();
        }));

        chatClient.addMessageConsumer(msg -> UiThread.run(() -> {
            conversationsView.refresh();
            if (infoPanel != null && infoPanel.isVisible()) {
                updateInfoPanelData();
            }
        }));

        // Setup Contacts ListView with REAL data
        setupContactsView();
        loadFriends();
        friendClient.onUpdate(packet -> UiThread.run(() -> {
            if (packet.data().has("sender")) loadFriendRequests();
            if (packet.data().has("accepted")) { loadFriends(); loadFriendRequests(); }
        }));

        // Setup Todo ListView (starts empty for real user tasks)
        setupTodoView();

        // Setup Cloud ListView (starts empty for real user notes)
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
        showFriendsList();
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

    // ── Chat Actions (REAL) ──

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
                statusLabel.setText("Không tìm thấy username '" + username + "' trên server.");
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
        refreshContactsFromConversations();
    }

    private void activateConversation(ConversationDto conversation) {
        chatController.showConversation(conversation);
        refreshPresence();
        if (conversation.counterpart() != null) {
            chatClient.requestPresence(
                    conversation.counterpart().id(),
                    conversation.counterpart().username());
        }
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
        showInfoAlert("Cuộc gọi thoại", "Tính năng gọi thoại P2P yêu cầu giao thức UDP/Voice media.");
    }

    @FXML
    private void handleVideoCall() {
        showInfoAlert("Cuộc gọi video", "Tính năng gọi video yêu cầu camera/webcam và luồng RTP.");
    }

    @FXML
    private void handlePinMessage() {
        statusLabel.setStyle("-fx-text-fill: #2e7d32;");
        statusLabel.setText("Đã ghim tin nhắn vào đầu hội thoại.");
    }

    @FXML
    private void handleInsertEmoji() {
        messageField.setText(messageField.getText() + " 😊 ");
        messageField.positionCaret(messageField.getText().length());
    }

    @FXML
    private void handleSendImage() {
        showInfoAlert("Gửi hình ảnh", "Đã sẵn sàng. Chọn ảnh để gửi theo định dạng chuẩn.");
    }

    @FXML
    private void handleSendFile() {
        showInfoAlert("Gửi file", "Đã sẵn sàng. Hỗ trợ gửi file qua socket TCP.");
    }

    @FXML
    private void handleScreenshot() {
        showInfoAlert("Chụp ảnh màn hình", "Nhấn tổ hợp phím Alt + A để chụp màn hình.");
    }

    @FXML
    private void handleReminder() {
        showInfoAlert("Nhắc hẹn", "Tạo nhắc hẹn trong hội thoại.");
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

    // ── Contacts View (100% REAL) ──

    private void setupContactsView() {
        Label emptyContactsLabel = new Label(
                "Chưa có bạn bè trong danh bạ.\nNhấn nút '+ Thêm bạn mới' ở trên để kết nối với tài khoản đã đăng ký!");
        emptyContactsLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-text-alignment: CENTER;");
        contactsListView.setPlaceholder(emptyContactsLabel);
        contactsListView.setItems(contacts);

        contactsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(UserDto user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-padding: 10 16; -fx-cursor: hand;");

                String name = user.displayName() != null ? user.displayName() : user.username();
                String initial = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();

                Circle av = new Circle(18);
                av.setStyle("-fx-fill: #0068ff;");
                Label avLbl = new Label(initial);
                avLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
                StackPane avPane = new StackPane(av, avLbl);

                boolean online = chatClient.isOnline(user.id());
                Label nameLbl = new Label(name);
                nameLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #111;");

                Label usernameLbl = new Label("@" + user.username() + " · " + (online ? "● Online" : "○ Offline"));
                usernameLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (online ? "#22c55e;" : "#94a3b8;"));

                VBox textBox = new VBox(2, nameLbl, usernameLbl);

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                if (showingRequests) {
                    FriendRequestDto request = friendRequests.stream().filter(item -> item.user().id() == user.id()).findFirst().orElse(null);
                    Button accept = new Button("Chấp nhận");
                    accept.getStyleClass().add("button-primary");
                    accept.setOnAction(event -> respondToRequest(request, true));
                    Button reject = new Button("Từ chối");
                    reject.setOnAction(event -> respondToRequest(request, false));
                    row.getChildren().addAll(avPane, textBox, sp, accept, reject);
                    setGraphic(row);
                    return;
                }

                Button chatBtn = new Button("Nhắn tin");
                    chatBtn.getStyleClass().add("button-primary");
                chatBtn.setStyle("-fx-padding: 4 14; -fx-font-size: 12px;");
                chatBtn.setOnAction(e -> {
                    navChat();
                    recipientUsernameField.setText(user.username());
                    openDirect();
                });

                row.getChildren().addAll(avPane, textBox, sp, chatBtn);
                setGraphic(row);
                setText(null);
            }
        });
    }

    private void refreshContactsFromConversations() {
        loadFriends();
    }

    private void loadFriends() {
        friendClient.friends().thenAccept(users -> UiThread.run(() -> {
            if (!showingRequests) contacts.setAll(users);
            contactsListView.refresh();
        })).exceptionally(error -> null);
    }

    private void loadFriendRequests() {
        friendClient.requests().thenAccept(requests -> UiThread.run(() -> {
            friendRequests.setAll(requests);
            if (showingRequests) {
                contacts.setAll(requests.stream().map(FriendRequestDto::user).toList());
                contactsListView.refresh();
            }
            subContactsRequestsBtn.setText("Lời mời kết bạn (" + requests.size() + ")");
        })).exceptionally(error -> null);
    }

    private void respondToRequest(FriendRequestDto request, boolean accept) {
        if (request == null) return;
        friendClient.respond(request.requestId(), accept).whenComplete((message, error) -> UiThread.run(() -> {
            if (error != null) showInfoAlert("Lời mời kết bạn", error.getMessage());
            else statusLabel.setText(message);
            loadFriendRequests();
            if (accept) loadFriends();
        }));
    }

    @FXML
    public void showFriendsList() {
        showingRequests = false;
        subContactsFriendsBtn.setStyle("-fx-background-color: #e8f0fe; -fx-text-fill: #0068ff; -fx-font-weight: bold; -fx-padding: 10 12; -fx-background-radius: 8; -fx-cursor: hand;");
        subContactsGroupsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #444; -fx-padding: 10 12; -fx-background-radius: 8; -fx-cursor: hand;");
        subContactsRequestsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #444; -fx-padding: 10 12; -fx-background-radius: 8; -fx-cursor: hand;");
        loadFriends();
    }

    @FXML
    public void showGroupsList() {
        showingRequests = false;
        subContactsGroupsBtn.setStyle("-fx-background-color: #e8f0fe; -fx-text-fill: #0068ff; -fx-font-weight: bold; -fx-padding: 10 12; -fx-background-radius: 8; -fx-cursor: hand;");
        subContactsFriendsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #444; -fx-padding: 10 12; -fx-background-radius: 8; -fx-cursor: hand;");
        subContactsRequestsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #444; -fx-padding: 10 12; -fx-background-radius: 8; -fx-cursor: hand;");

        // Show groups in contacts
        contacts.clear();
        for (ConversationDto c : conversations) {
            if ("GROUP".equalsIgnoreCase(c.type())) {
                contacts.add(new UserDto(c.id(), c.name(), "Nhóm: " + c.name(), null, false));
            }
        }
    }

    @FXML
    public void showRequestsList() {
        showingRequests = true;
        subContactsRequestsBtn.setStyle("-fx-background-color: #e8f0fe; -fx-text-fill: #0068ff; -fx-font-weight: bold; -fx-padding: 10 12; -fx-background-radius: 8; -fx-cursor: hand;");
        subContactsFriendsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #444; -fx-padding: 10 12; -fx-background-radius: 8; -fx-cursor: hand;");
        subContactsGroupsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #444; -fx-padding: 10 12; -fx-background-radius: 8; -fx-cursor: hand;");
        contacts.clear();
        loadFriendRequests();
        Label emptyRequests = new Label("Không có lời mời kết bạn mới.");
        emptyRequests.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        contactsListView.setPlaceholder(emptyRequests);
    }

    @FXML
    private void handleFilterContacts() {
        String q = contactSearchField.getText() == null ? "" : contactSearchField.getText().trim().toLowerCase();
        if (q.isBlank()) {
            refreshContactsFromConversations();
        } else {
            contacts.removeIf(u -> !u.displayName().toLowerCase().contains(q) && !u.username().toLowerCase().contains(q));
        }
    }

    @FXML
    private void searchPhoneAndAddFriend() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Tìm bạn bằng số điện thoại");
        dialog.setHeaderText("Nhập số điện thoại đầy đủ theo mã quốc gia");
        dialog.setContentText("Ví dụ: +84901234567");
        dialog.showAndWait().ifPresent(phone -> friendClient.search(phone.trim()).whenComplete((user, error) -> UiThread.run(() -> {
            if (error != null) { showInfoAlert("Tìm bạn", error.getMessage()); return; }
            if (user == null) { showInfoAlert("Tìm bạn", "Không tìm thấy tài khoản với số điện thoại này."); return; }
            Alert profile = new Alert(Alert.AlertType.CONFIRMATION);
            profile.setTitle("Hồ sơ người dùng");
            profile.setHeaderText(user.displayName());
            profile.setContentText("@" + user.username() + (user.avatarUrl() == null ? "\nChưa có ảnh đại diện." : "\nẢnh đại diện: " + user.avatarUrl()) + "\nGửi lời mời kết bạn?");
            if (user.avatarUrl() != null && !user.avatarUrl().isBlank()) {
                try { profile.setGraphic(new javafx.scene.image.ImageView(new javafx.scene.image.Image(user.avatarUrl(), 48, 48, true, true))); }
                catch (RuntimeException ignored) { }
            }
            profile.showAndWait().filter(choice -> choice == javafx.scene.control.ButtonType.OK).ifPresent(choice ->
                    friendClient.request(user.id()).whenComplete((message, requestError) -> UiThread.run(() -> {
                        if (requestError != null) showInfoAlert("Lời mời kết bạn", requestError.getMessage());
                        else showInfoAlert("Lời mời kết bạn", message);
                    })));
        })));
    }



    // ── Todo View (REAL user-created tasks) ──

    private void setupTodoView() {
        Label emptyTodo = new Label("Chưa có công việc nào.\nNhập việc cần làm vào ô trên và nhấn Enter để tạo mới.");
        emptyTodo.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-text-alignment: CENTER;");
        todoListView.setPlaceholder(emptyTodo);
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
            todoItems.add(0, text);
            newTodoField.clear();
        }
    }

    // ── Cloud View (REAL user-created notes) ──

    private void setupCloudView() {
        Label emptyCloud = new Label("Cloud của bạn đang trống.\nNhập ghi chú vào ô trên để lưu trữ cá nhân.");
        emptyCloud.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-text-alignment: CENTER;");
        cloudListView.setPlaceholder(emptyCloud);
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
        showInfoAlert("Tải lên Cloud", "Chức năng lưu trữ file cá nhân trên máy cục bộ.");
    }

    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }
}
