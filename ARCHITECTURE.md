# Zalo Clone – Kiến trúc & Kế hoạch (chưa code)

Dựa trên slide "Secure TCP Chat – Java" (Chương 16) và bảng tiêu chí CHAT.
Gốc package: `com.example.zalo`.

## 1. Nguyên tắc rút ra từ slide (bắt buộc giữ)

| # | Nguyên tắc | Áp dụng |
|---|-----------|---------|
| 1 | TCP + TLS 1.2/1.3 bằng `SSLServerSocket` / `SSLSocket` | Mọi dữ liệu (kể cả login, file) đều đi qua TLS |
| 2 | Mỗi client = 1 Socket + 1 SSLSocket + 1 ClientId + 1 luồng xử lý riêng | `ClientHandler implements Runnable`, chạy bằng `ExecutorService` |
| 3 | Server giữ Private Key (`server.p12`), client chỉ có `server.crt` | `scripts/gen-certs.*`, `.gitignore` chặn `*.p12` |
| 4 | Giao thức JSON, 1 dòng = 1 message, kết thúc `\n` | `BufferedReader.readLine()`, giới hạn 64KB/dòng |
| 5 | State: `DISCONNECTED → TCP_CONNECTED → TLS_READY → AUTHENTICATED → CHATTING → CLOSED` | Chỉ cho gửi tin khi `AUTHENTICATED` |
| 6 | `ConcurrentHashMap<Integer, ClientSession>` + `ReentrantLock sendLock` mỗi session | Chống ghi xen kẽ byte khi nhiều thread cùng gửi |
| 7 | Broadcast duyệt snapshot, 1 client lỗi không ảnh hưởng client khác | `SessionRegistry.broadcast()` |
| 8 | Ngắt kết nối: remove 1 lần → đóng tài nguyên (finally) → thông báo → log | `cleanup()` idempotent |
| 9 | Client kiểm tra cert: hostname (SAN/CN), hạn dùng, chuỗi tin cậy | `ServerCertificateValidator` |
| 10 | Không log password/token/nội dung tin nhắn; password lưu BCrypt | `logback.xml`, `PasswordHasher` |
| 11 | Client: luồng nhận riêng, UI cập nhật qua `Platform.runLater` | Không đọc socket trên UI thread |

## 2. Quyết định kiến trúc

| Hạng mục | Chốt | Lý do |
|---|---|---|
| Mô hình | Client-Server; chat 1-1 và nhóm đều định tuyến qua server | Đúng slide; server mới lưu được lịch sử và trạng thái |
| Giao vận | TCP + TLS (`SSLServerSocket` / `SSLSocket`) | Slide yêu cầu mã hóa toàn bộ |
| Đồng thời | Blocking I/O, 1 luồng cho mỗi client, chạy bằng `ExecutorService` (thread pool giới hạn) | Đúng slide; đủ cho vài trăm client |
| Giao thức | JSON, 1 dòng = 1 message, tối đa 64KB/dòng | Đúng slide |
| File, ảnh, video, âm thanh | Chia chunk 32KB, Base64 trong JSON, gửi qua cùng kết nối TLS | Không cần thêm cổng hay giao thức mới |
| CSDL server | **PostgreSQL 16 chạy trong Docker** (`docker-compose.yml`); file thật lưu ở `data/uploads` | Nhiều luồng ghi cùng lúc ổn định hơn SQLite; dữ liệu nằm trong volume `pgdata` |
| Truy cập DB | **Chỉ Java Server** dùng JDBC. Luồng: Client (Socket/TLS) → Server đa luồng → JDBC (HikariCP) → PostgreSQL | Client không giữ mật khẩu DB; server kiểm soát logic (ai online, báo realtime cho người nhận ngay sau khi lưu DB thành công) |
| Connection pool | HikariCP, `maximumPoolSize=10`, cấm `DriverManager.getConnection()` | 1 luồng/client mà mở-đóng JDBC thủ công sẽ gây `FATAL: sorry, too many clients already` hoặc giật lag |
| Cache client | **SQLite** ở máy client (`data/cache/<username>.db`) | Xem lại lịch sử khi mất mạng; chỉ là bản sao, nguồn sự thật là server |
| Docker | Chỉ dùng cho PostgreSQL. Server Java và Client JavaFX chạy trên máy | Client có giao diện đồ họa không hợp với container; có thể đưa server vào container sau nếu cần |
| Giao diện | JavaFX | Có sẵn `MediaPlayer` cho video và âm thanh |
| Server | 4 tầng: `net` → `handler` → `service` → `repository/storage` | Tầng trên chỉ gọi tầng dưới |
| Client | Tách `net` / `service` / `state` / `ui`; luồng nhận riêng | Giao diện không bị treo |
| Build | Maven đa module, JDK 17 | Slide yêu cầu JDK 17+ |
| Không dùng | Spring, Netty, ORM (Hibernate) | Slide dạy socket thuần; framework làm lệch mục tiêu bài |

**Emoji:** tin nhắn gửi dạng text Unicode (không phụ thuộc phông chữ). Bảng chọn emoji dùng bộ ảnh PNG (ví dụ Twemoji) trong `resources/emoji`. Khi hiển thị, client đổi ký tự emoji thành ảnh tương ứng, vì JavaFX hiển thị emoji màu trên Windows không ổn định.

**Quy tắc truy cập DB (bắt buộc):**
1. Không có driver PostgreSQL, URL hay mật khẩu DB trong module `zalo-client`.
2. Mỗi truy vấn: lấy connection từ pool bằng try-with-resources, chạy xong trả lại ngay. Không giữ connection suốt vòng đời của một client.
3. Chỉ gửi `message` realtime cho người nhận sau khi `INSERT` thành công (đã commit).
4. `pool.maxSize` (10) phải nhỏ hơn `max_connections` của PostgreSQL (50 trong compose).
5. Mật khẩu DB đọc từ biến môi trường `DB_USER` / `DB_PASSWORD`, không để trong file cấu hình.

**Cache offline ở client:**
- Mở hội thoại: đọc SQLite hiển thị ngay → gửi `history_request` với `afterId = last_message_id` → ghi tin mới vào cache.
- Mất mạng: vẫn xem được lịch sử từ cache, hiện banner "Đang offline", khóa nút gửi (không làm hàng đợi gửi bù trong phạm vi bài).
- Cache chỉ lưu tin nhắn và metadata file; không lưu mật khẩu hay token. Mỗi tài khoản một file riêng. Truy cập SQLite trên một luồng nền riêng, không đụng luồng UI.

## 3. Ánh xạ tiêu chí CHAT → tính năng

(Mỗi ô gốc ×5 = điểm tổng: ô 0,5 → 2,5đ; ô 0,25 → 1,25đ.)

| Tiêu chí | Điểm | Cách làm | Thành phần chính |
|----------|------|----------|------------------|
| P2P (chat 1-1) | 2,5 | Hội thoại `DIRECT`, server định tuyến tới đúng người nhận | `ConversationService`, `ChatHandler` |
| Group | 2,5 | Hội thoại `GROUP`: tạo nhóm, thêm/xoá thành viên, gửi tới các thành viên | `GroupService`, `conversation_members` |
| File | 2,5 | Upload/download theo chunk, lưu `data/uploads` | `FileTransferService`, `FileStorage` |
| Emoji | 2,5 | Text Unicode + bảng chọn emoji bằng ảnh PNG | `EmojiPicker` (client) |
| History | 2,5 | Lưu PostgreSQL, tải theo trang (cuộn lên để xem thêm); client cache SQLite để xem offline | `MessageRepository`, `history_request`, `MessageCache` (client) |
| Image | 1,25 | Upload như file, client hiển thị ảnh thu nhỏ | `ImageMessageCell` |
| Video | 1,25 | Upload như file, client phát bằng `MediaPlayer` | `MediaMessageCell` |
| Sound | 1,25 | Upload file âm thanh, client phát bằng `MediaPlayer` | `MediaMessageCell` |
| State | 2,5 | Presence ONLINE/OFFLINE + trạng thái tin SENT/DELIVERED/SEEN | `PresenceService`, `ReceiptRepository`, `ReceiptNotifier`, `MarkSeenHandler` (server); `ChatClient` xử lý `presence`/`message_status`, `MessageCell` hiển thị ✓/✓✓ màu xanh khi SEEN |
| Time | 2,5 | Server gán `sentAt` (epoch millis), client định dạng `HH:mm` và nhóm theo ngày | `TimeUtil` |
| Sender | 1,25 | Mỗi tin có `senderId`, `senderName`, avatar | `MessageDto` |

## 4. Thư viện

| Thư viện | Module | Công dụng |
|---|---|---|
| JDK `javax.net.ssl` / `java.net` / `java.security` (có sẵn) | server, client | TLS, socket, đọc keystore và chứng chỉ |
| `jackson-databind` 2.17.2 | common | Chuyển object ↔ JSON cho mọi message |
| `jackson-datatype-jsr310` | common | Xử lý kiểu thời gian trong JSON (tiêu chí Time) |
| `org.postgresql:postgresql` 42.7.4 | server | Driver JDBC kết nối PostgreSQL |
| `HikariCP` 5.1.0 | server | Pool kết nối DB, nhiều luồng dùng chung vài connection, tránh tràn `max_connections` |
| `sqlite-jdbc` 3.46.1.0 | client | Cache tin nhắn cũ ở máy người dùng để xem khi mất mạng |
| Docker + Docker Compose (công cụ, không phải thư viện) | máy dev | Chạy PostgreSQL bằng `docker-compose.yml`, không cần cài PostgreSQL trực tiếp |
| `at.favre.lib:bcrypt` 0.10.2 | server | Băm mật khẩu bằng BCrypt, không lưu plain text |
| `slf4j-api` + `logback-classic` | common, server, client | Ghi log ra console và file xoay vòng |
| `javafx-controls` 21.0.4 | client | Các control giao diện: danh sách, nút, ô nhập |
| `javafx-fxml` | client | Thiết kế giao diện bằng file `.fxml` |
| `javafx-media` | client | Phát âm thanh và video trong khung chat |
| `junit-jupiter` 5.10.3 | cả 3 module | Unit test |
| `mockito-core` 5.12.0 | server, client | Giả lập repository hoặc socket khi test service |
| `maven-shade-plugin` | server | Đóng gói thành `zalo-server.jar` chạy được |
| `javafx-maven-plugin` | client | Chạy client bằng `mvn javafx:run` |

Phiên bản ghi theo trí nhớ, cần chạy `mvn clean package` để xác nhận tải được.

## 5. Cấu trúc thư mục (root → trong) và công dụng package

```
ZaloClone/
├── pom.xml                         # parent POM: quản lý version, multi-module
├── .gitignore                      # chặn *.p12, *.key, .env, data/, logs/
├── docker-compose.yml              # PostgreSQL 16 (cổng 5432 chỉ mở cho 127.0.0.1)
├── .env.example                    # mẫu DB_USER, DB_PASSWORD, CHAT_CERT_PASSWORD (copy thành .env)
├── ARCHITECTURE.md
├── README.md
├── docs/                           # sơ đồ, báo cáo, test case
├── scripts/
│   ├── gen-certs.sh                # tạo server.p12 + server.crt (keytool)
│   └── gen-certs.bat
│
├── zalo-common/                    # dùng chung Server + Client
│   └── src/main/java/com/example/zalo/common/
│       ├── protocol/               # MessageType, Packet, mã lỗi, hằng số giao thức
│       ├── model/                  # DTO: UserDto, MessageDto, ConversationDto, AttachmentDto, trạng thái
│       └── util/                   # JsonUtil (Jackson), TimeUtil
│
├── zalo-server/
│   ├── certificates/               # server.p12 (KHÔNG commit)
│   ├── data/                       # uploads/ (runtime, KHÔNG commit)
│   └── src/main/
│       ├── java/com/example/zalo/server/
│       │   ├── ServerMain          # điểm khởi động
│       │   ├── config/             # đọc server.properties, tạo SSLContext, tạo HikariDataSource, chạy schema.sql
│       │   ├── net/                # accept loop, ClientHandler đọc từng dòng JSON, ghi gói tin
│       │   ├── session/            # ClientSession, SessionRegistry, enum trạng thái, sendLock
│       │   ├── handler/            # 1 handler / MessageType: login, chat, group, file, history, presence, ping
│       │   ├── service/            # nghiệp vụ: auth, conversation, group, message, presence, file transfer
│       │   ├── repository/         # JDBC cho user, conversation, message, attachment (nhận DataSource, không tự mở kết nối)
│       │   ├── security/           # băm mật khẩu, giới hạn đăng nhập sai, validate đầu vào
│       │   └── storage/            # ghi/đọc file thật; kiểm tra đuôi file và dung lượng
│       └── resources/              # server.properties, schema.sql (PostgreSQL), logback.xml
│
└── zalo-client/
    ├── certificates/               # server.crt (public, được commit)
    ├── data/cache/                 # <username>.db (SQLite cache, runtime, KHÔNG commit)
    └── src/main/
        ├── java/com/example/zalo/client/
        │   ├── ClientMain          # ứng dụng JavaFX
        │   ├── config/             # đọc client.properties, nạp server.crt làm truststore
        │   ├── cache/              # SQLite cache: MessageCache, đồng bộ theo last_message_id (không có kết nối PostgreSQL)
        │   ├── net/                # kết nối TLS, kiểm tra chứng chỉ, luồng nhận, gửi có khóa
        │   ├── service/            # hàm nghiệp vụ cho UI: đăng nhập, chat, gửi file, tải lịch sử
        │   ├── state/              # trạng thái kết nối, phiên đăng nhập, danh sách UI quan sát
        │   ├── ui/controller/      # controller từng màn hình: đăng nhập, chính, chat, nhóm
        │   ├── ui/component/       # bong bóng tin nhắn, ô ảnh, ô media, bảng chọn emoji
        │   └── util/               # chuyển việc về luồng UI, xử lý ảnh
        └── resources/              # client.properties, cache-schema.sql (SQLite), logback.xml, fxml/, css/, images/, emoji/, sounds/
```

Phân tầng server:

```
net (ChatServer, ClientHandler)   ← socket / TLS / đọc-ghi dòng JSON
   ↓ Packet
handler (theo MessageType)        ← kiểm tra state + validate input
   ↓
service (nghiệp vụ)               ← auth, chat, group, file, presence
   ↓
repository + storage              ← SQLite, file system
```

## 6. Giao thức (mở rộng từ slide: login / login_result / message / system / disconnect)

Mỗi dòng: `{"type":"...", "requestId":"...", ...}`

| Nhóm | Client → Server | Server → Client |
|------|-----------------|-----------------|
| Auth | `register`, `login`, `disconnect` | `register_result`, `login_result` |
| Chat | `send_message` (conversationId, kind=TEXT/EMOJI) | `message` (senderId, senderName, sentAt) |
| Direct/Group | `open_direct`, `create_group`, `add_member`, `remove_member`, `leave_group` | `conversation_updated`, `system` |
| File/Media | `file_begin`, `file_chunk`, `file_end`, `file_download` | `file_ready`, `file_chunk`, `file_end` |
| History | `history_request` (conversationId, `beforeId` để cuộn lên cũ hơn, `afterId` để đồng bộ tin mới vào cache, limit) | `history_response` |
| State | `mark_seen`, `ping` | `presence`, `message_status`, `pong` |
| Lỗi | – | `error` (INVALID_JSON, NOT_AUTHENTICATED, UNKNOWN_TYPE, TOO_LARGE ...) |

Server từ chối mọi type (trừ `register`/`login`) khi session chưa `AUTHENTICATED`.

## 7. Cấu hình & bảo mật

- Mật khẩu keystore đọc từ biến môi trường `CHAT_CERT_PASSWORD`, không để trong file.
- `server.properties` / `client.properties`: cổng, TLS, giới hạn dung lượng, phân trang.
- `schema.sql` (PostgreSQL, chạy lại không lỗi): users, friendships, conversations, conversation_members, attachments, messages, message_receipts. Server tự chạy khi khởi động.
- `cache-schema.sql` (SQLite ở client): cached_conversations, cached_messages, sync_state.
- `docker-compose.yml`: PostgreSQL có healthcheck, volume `pgdata`, `max_connections=50`, cổng chỉ bind `127.0.0.1`.
- BCrypt cho mật khẩu, giới hạn thử đăng nhập sai, validate đuôi file và dung lượng, giới hạn 64KB/dòng.

## 8. Kế hoạch thực hiện

Chi tiết từng bước (file cần code, tiêu chí nghiệm thu) nằm ở [`docs/ROADMAP.md`](docs/ROADMAP.md). Thứ tự tóm tắt:

0. Setup môi trường → 1. `common` (giao thức, DTO) → 2. Server hạ tầng (cấu hình, DB, TLS) → 3. Server nhận kết nối → 4. Đăng nhập (server) → 5. Client lõi + màn hình Login (**demo đầu tiên**) → 6. Chat 1-1 (kèm Time, Sender) → 7. History + cache → 8. State → 9. Group → 10. Emoji → 11. File → 12. Image/Video/Sound → 13. Kiểm thử và hoàn thiện.

Kiểm thử cuối: bảng TC01–TC06 của slide; 5 client nhắn liên tục không báo `too many clients`; tắt mạng client vẫn xem được lịch sử từ cache; unit test service bằng JUnit + Mockito.

## 9. Chạy thử (khi đã có code)

```bash
cp .env.example .env                # đổi mật khẩu
export DB_USER=zalo_app DB_PASSWORD='...' CHAT_CERT_PASSWORD='...'
docker compose up -d                # PostgreSQL
./scripts/gen-certs.sh localhost
mvn clean package
java -jar zalo-server/target/zalo-server.jar
mvn -pl zalo-client javafx:run      # mở nhiều cửa sổ để test nhiều client
```
