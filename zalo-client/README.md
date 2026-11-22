# Zalo Client Module (`zalo-client`)

Ứng dụng máy tính (Desktop App) giao diện đồ hoạ người dùng được phát triển bằng **JavaFX 21**. Ứng dụng kết nối tới **Zalo Server** thông qua kênh truyền bảo mật **TLS Socket** (`SSLSocket`), tích hợp cơ chế lưu trữ đệm **SQLite** cục bộ cho phép xem lịch sử tin nhắn khi mất mạng (offline mode).

---

## 1. Kiến trúc Tổng thể & Thiết kế Đa luồng

Ứng dụng Client được tổ chức theo mô hình tách bạch giữa Tầng mạng (Network), Tầng dịch vụ (Service), Trạng thái (State), Bộ nhớ đệm (Cache) và Giao diện người dùng (UI):

```
                   ┌───────────────────────────────────┐
                   │       GIAO DIỆN (JavaFX UI)       │
                   │ • MainController, ChatController  │
                   │ • LoginController, RegisterCtrl   │
                   │ • MessageCell, ConversationCell   │
                   └─────────────────▲─────────────────┘
                                     │ UiThread.run() (Platform.runLater)
                   ┌─────────────────┴─────────────────┐
                   │    DỊCH VỤ CLIENT (Asynchronous)   │
                   │ • AuthClient (CompletableFuture)  │
                   │ • ChatClient (Direct, Group, Msg) │
                   │ • FriendClient (Phone, Requests)  │
                   └───────▲───────────────────▲───────┘
                           │                   │
            (Offline/Sync) │                   │ (Packet / Response)
                           ▼                   ▼
    ┌──────────────────────────┐    ┌──────────────────────────┐
    │     BỘ NHỚ ĐỆM CỤC BỘ    │    │      TẦNG MẠNG (TLS)     │
    │ • MessageCache (SQLite)  │    │ • TcpClient              │
    │ • `data/cache/<user>.db` │    │ • ReceiveLoop (Luồng đọc)│
    │ • Luồng background riêng │    │ • PacketSender (sendLock)│
    └──────────────────────────┘    └────────────┬─────────────┘
                                                 │ TLSv1.3 (Port 5000)
                                                 ▼
                                         [ Zalo Server ]
```

### 1.1 Nguyên tắc An toàn Luồng (Thread Safety trong JavaFX)
1. **Luồng nhận riêng biệt (`ReceiveLoop`):** Socket đọc dữ liệu liên tục bằng `BufferedReader.readLine()` trên một thread nền độc lập. Tuyệt đối không đọc socket trên JavaFX Application Thread để tránh làm treo ứng dụng.
2. **Cập nhật UI an toàn qua `UiThread.run()`:** Bất kỳ sự kiện nào từ tầng mạng (nhận tin nhắn mới, có người online/offline, cập nhật trạng thái đã xem) đều được chuyển giao cho UI thông qua `Platform.runLater()`.
3. **Gửi tin nhắn có khóa bảo vệ (`PacketSender`):** Có khóa `ReentrantLock` đảm bảo các thread giao diện hoặc thread heartbeat không ghi đè dữ liệu lên stream của nhau.

---

## 2. Bảo mật Kết nối & Xác thực Chứng chỉ (TLS / PKI)

- **Mô hình Public-Key Infrastructure:**
  - Client chỉ giữ chứng chỉ công khai của Server: `certificates/server.crt` (public certificate).
  - Client **tuyệt đối không** có Private Key (`server.p12`) và **không bao giờ** kết nối hay giữ thông tin tài khoản của cơ sở dữ liệu PostgreSQL.
- **Xác thực chứng chỉ nghiêm ngặt (`ServerCertificateValidator`):**
  - Khi bắt tay TLS, Client kiểm tra tính hợp lệ của chuỗi chứng chỉ thông qua `TrustStoreLoader`.
  - Đối chiếu tên máy chủ kết nối với thông tin `CN` (Common Name) hoặc `SAN` (Subject Alternative Name) có trong chứng chỉ:
    ```properties
    # Cấu hình trong client.properties
    tls.expectedHost=localhost
    ```
  - Kiểm tra thời hạn hiệu lực (chống dùng chứng chỉ hết hạn).

---

## 3. Cơ chế Lưu trữ Đệm Ngoại tuyến (Offline Cache với SQLite)

Nhằm đáp ứng trải nghiệm mượt mà và cho phép người dùng xem lại lịch sử ngay cả khi mất mạng:
1. **File lưu trữ độc lập theo tài khoản:** Mỗi tài khoản đăng nhập có một file database SQLite riêng biệt tại: `data/cache/<username>.db`. Tránh rò rỉ dữ liệu giữa các tài khoản dùng chung một máy tính.
2. **Cấu trúc bảng cache (`cache-schema.sql`):**
   - `cached_conversations`: Danh sách hội thoại 1-1 và nhóm đã tham gia.
   - `cached_messages`: Toàn bộ tin nhắn đã nhận/gửi kèm trạng thái và metadata file.
   - `sync_state`: Lưu mốc `last_message_id` của từng cuộc trò chuyện.
3. **Quy trình Đồng bộ Tin nhắn Thông minh:**
   - **Bước 1:** Khi mở một cuộc hội thoại, Client lập tức đọc dữ liệu từ SQLite hiển thị lên màn hình ngay lập tức (không có độ trễ).
   - **Bước 2:** Gửi gói tin `history_request` lên Server với tham số `afterId = last_message_id`.
   - **Bước 3:** Server trả về các tin nhắn phát sinh mới kể từ mốc đó, Client lưu bổ sung vào SQLite và cập nhật UI.
   - **Khi mất mạng:** UI hiển thị thanh thông báo: *"Đang offline: hiển thị lịch sử đã lưu."*, vô hiệu hoá nút gửi tin nhưng vẫn xem được toàn bộ nội dung cũ.

---

## 4. Giao diện & Các Tính năng Chính

Giao diện được thiết kế hiện đại theo phong cách Zalo, chia làm các khu vực chính:

### 4.1 Thanh Điều hướng Bên trái (Navigation Bar)
- **Avatar & Trạng thái người dùng:** Bấm vào để xem và chỉnh sửa thông tin cá nhân (`ProfileDialog`).
- **Tab Tin nhắn (Chat View):** Hiển thị danh sách hội thoại, số tin chưa đọc, thời gian tin nhắn mới nhất và chấm trạng thái online/offline của bạn bè.
- **Tab Danh bạ (Contacts View):**
  - **Tìm kiếm bạn bè qua Số điện thoại:** Nhập số điện thoại để tìm chính xác người dùng và gửi lời mời kết bạn.
  - **Danh sách Lời mời kết bạn:** Hiển thị các lời mời đang chờ (`PENDING`), có nút Chấp nhận hoặc Từ chối.
  - **Danh bạ bạn bè:** Danh sách bạn bè kèm trạng thái online thời gian thực.
- **Tab Danh sách việc (To-Do List) & Cloud cá nhân:** Ghi chú cá nhân và tài liệu lưu trữ.
- **Cài đặt (Settings):** Cấu hình ngôn ngữ, âm báo, giao diện.

### 4.2 Khung Trò chuyện Trực tiếp (Chat Area)
- **Chat 1-1 (Direct) & Chat Nhóm (Group):**
  - Hỗ trợ tạo nhóm chat mới với nhiều thành viên.
  - Thêm thành viên, xoá thành viên, rời khỏi nhóm.
- **Bong bóng tin nhắn thông minh (`MessageCell`):**
  - Phân biệt rõ tin nhắn gửi đi (bên phải, màu xanh Zalo `#e5efff`) và tin nhắn nhận được (bên trái, màu trắng viền xám).
  - Tên người gửi và avatar đại diện.
  - Định dạng thời gian rõ ràng: giờ phút (`HH:mm`) và ngày gửi.
  - **Dấu trạng thái tin nhắn (Receipt Ticks):**
    - ✓ : Tin đã gửi thành công lên máy chủ (`SENT`).
    - ✓✓ xám : Tin đã được phân phối tới máy người nhận (`DELIVERED`).
    - ✓✓ xanh : Người nhận đã mở xem tin nhắn (`SEEN`).
- **Phân trang lịch sử (History Pagination):**
  - Tải trước 50 tin nhắn gần nhất.
  - Nút *"Tải thêm tin cũ"* (`loadOlderButton`) cho phép cuộn ngược thời gian để xem các tin trước đó mà không làm tràn bộ nhớ.

---

## 5. Cấu hình Ứng dụng (`client.properties`)

Đường dẫn: `zalo-client/src/main/resources/client.properties`

```properties
# ===== Địa chỉ kết nối Server =====
server.host=127.0.0.1
server.port=5000
connect.timeoutMillis=5000

# ===== Cấu hình TLS =====
tls.truststore.path=certificates/server.crt
tls.protocols=TLSv1.3,TLSv1.2
tls.expectedHost=localhost

# ===== Giữ kết nối & Tự động kết nối lại =====
heartbeat.intervalSeconds=30
reconnect.maxAttempts=5
reconnect.delaySeconds=3

# ===== Tải tệp =====
download.dir=downloads
upload.chunkBytes=32768

# ===== Cache SQLite Cục bộ =====
cache.enabled=true
cache.dir=data/cache
cache.maxMessagesPerConversation=2000

# ===== Tùy biến UI =====
ui.title=Zalo Clone
ui.theme=light
ui.timeFormat=HH:mm
ui.dateFormat=dd/MM/yyyy
ui.historyPageSize=50
```

---

## 6. Hướng dẫn Khởi chạy Client

### 6.1 Điều kiện cần
- Đã cài đặt **JDK 17 trở lên**.
- **Zalo Server** đã được khởi động và đang lắng nghe ở cổng `5000`.
- Đã có file chứng chỉ `zalo-client/certificates/server.crt` (được tạo ra sau khi chạy lệnh sinh chứng chỉ).

### 6.2 Lệnh khởi chạy
Tại thư mục gốc của dự án (`ZaloClone`):

```bash
# Khởi chạy Client bằng Maven JavaFX Plugin
mvn -pl zalo-client javafx:run
```

### 6.3 Hướng dẫn Mở nhiều Client để Thử nghiệm (Multi-client Testing)
Để kiểm tra tính năng trò chuyện thời gian thực giữa 2 người dùng:
1. Mở cửa sổ Terminal thứ nhất:
   ```bash
   mvn -pl zalo-client javafx:run
   ```
   Đăng ký tài khoản: `userA` (Mật khẩu: `123456`, Tên: `Nguyễn Văn A`, SĐT: `0901234567`).
2. Mở thêm một cửa sổ Terminal thứ hai:
   ```bash
   mvn -pl zalo-client javafx:run
   ```
   Đăng ký tài khoản: `userB` (Mật khẩu: `123456`, Tên: `Trần Thị B`, SĐT: `0987654321`).
3. Từ `userA`, vào mục Danh bạ -> Nhập số điện thoại `0987654321` -> Bấm **Kết bạn**.
4. Cửa sổ `userB` sẽ nhận được thông báo lời mời kết bạn ngay lập tức -> Bấm **Đồng ý**.
5. Cả hai người dùng bắt đầu nhắn tin qua lại và quan sát:
   - Tin nhắn hiển thị tức thì.
   - Trạng thái tin chuyển từ đã gửi sang đã xem.
   - Thử ngắt Server và mở lại để kiểm tra tính năng xem tin offline từ cache SQLite.

---

## 7. Kết nối qua Mạng LAN (Chạy giữa 2 máy tính khác nhau)

Nếu bạn muốn chạy Server ở Máy A và chạy Client ở Máy B trong cùng mạng WiFi / LAN:
1. **Trên Máy Server (Máy A):**
   - Xác định IP nội bộ của Máy A (ví dụ: `192.168.1.15`).
   - Sinh chứng chỉ với IP này:
     ```bash
     # Trên Linux/macOS
     CHAT_CERT_PASSWORD='mat_khau_cert' ./scripts/gen-certs.sh 192.168.1.15
     # Hoặc trên Windows
     set CHAT_CERT_PASSWORD=mat_khau_cert && scripts\gen-certs.bat 192.168.1.15
     ```
   - Mở cổng `5000` trên Windows Firewall / Linux UFW.
2. **Trên Máy Client (Máy B):**
   - Copy file `server.crt` vừa tạo từ Máy A vào thư mục `zalo-client/certificates/server.crt` của Máy B.
   - Sửa file `zalo-client/src/main/resources/client.properties`:
     ```properties
     server.host=192.168.1.15
     tls.expectedHost=192.168.1.15
     ```
   - Chạy lệnh: `mvn -pl zalo-client javafx:run`.
