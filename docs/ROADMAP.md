# Lộ trình code – làm cái nào trước

Nguyên tắc sắp xếp:
1. **Làm theo lát cắt dọc**: xong một bước là chạy và kiểm tra được ngay, không viết một đống rồi mới chạy.
2. **Nền móng trước, tính năng sau**: TLS + đăng nhập mà chưa chạy thì mọi tính năng khác đều vô nghĩa.
3. **Tính năng 2,5đ làm trước, 1,25đ làm sau** (Image / Video / Sound / Sender là phần dễ, làm cuối).
4. Mỗi bước có mục **Xong khi** để tự nghiệm thu trước khi sang bước kế.

Tổng quan thứ tự:

```
0 Setup ─► 1 common ─► 2 Server hạ tầng ─► 3 Server kết nối ─► 4 Đăng nhập (server)
                                                                      │
                                   5 Client lõi + màn hình Login ◄────┘
                                                │
                  6 Chat 1-1 (Time + Sender) ─► 7 History + cache ─► 8 State
                                                │
                                   9 Group ─► 10 Emoji ─► 11 File ─► 12 Image/Video/Sound
                                                                      │
                                                         13 Kiểm thử + hoàn thiện
```

---

## Bước 0 – Setup (đã có bộ khung; việc còn lại là kiểm tra máy)

Việc cần làm:
- Cài JDK 17+, Maven 3.9+, Docker Desktop, IntelliJ IDEA (mở thư mục gốc, chọn Import Maven project).
- Copy `.env.example` → `.env`, đổi mật khẩu, export `DB_USER`, `DB_PASSWORD`, `CHAT_CERT_PASSWORD`.
- `docker compose up -d`, tạo chứng chỉ bằng `scripts/gen-certs.*`.

**Xong khi:** `java -version` ≥ 17; `mvn clean package` chạy qua cả 3 module (dù chưa có code); `docker compose ps` báo `healthy`; có `server.p12` ở server và `server.crt` ở client.

## Bước 1 – `zalo-common` (giao thức dùng chung)

Code theo thứ tự:
1. `protocol/ProtocolConstants` (cổng, giới hạn dòng, charset)
2. `protocol/MessageType` (enum: login, login_result, send_message, message, ping, pong, error, system, disconnect…)
3. `protocol/ErrorCode`
4. `protocol/Packet` (type, requestId, dữ liệu kèm theo)
5. `util/JsonUtil` (đổi Packet ↔ chuỗi JSON một dòng)
6. `util/TimeUtil`
7. `model/UserDto`, `model/MessageDto` (bản tối thiểu, các DTO khác thêm sau khi cần)

**Xong khi:** unit test chuyển `Packet` → JSON → `Packet` ra đúng dữ liệu cũ; JSON luôn nằm trên một dòng, có tiếng Việt và emoji vẫn đúng.

## Bước 2 – Server hạ tầng (cấu hình, DB, TLS)

1. `config/ServerConfig` (đọc `server.properties`, đọc biến môi trường)
2. `config/Database` (HikariCP + chạy `schema.sql`)
3. `config/SslContextFactory` (nạp `server.p12` thành `SSLContext`)
4. `ServerMain` (bản đầu: nạp cấu hình, nối DB, tạo SSLContext, in log rồi thoát)

**Xong khi:** chạy `ServerMain` thấy log "DB connected, schema ready" và "TLS context loaded"; mở `psql` thấy đủ các bảng; sai mật khẩu keystore thì server báo lỗi rõ ràng và dừng.

## Bước 3 – Server nhận kết nối (chưa có đăng nhập)

1. `session/ClientState` (enum trạng thái)
2. `session/ClientSession` (socket, sendLock, trạng thái)
3. `session/SessionRegistry` (`ConcurrentHashMap`)
4. `net/PacketWriter` (ghi một dòng JSON, có khóa)
5. `net/ClientHandler` (đọc từng dòng, parse, chuyển cho handler, dọn dẹp trong `finally`)
6. `net/ChatServer` (accept loop + `ExecutorService`)
7. `handler/PingHandler` + bộ định tuyến handler theo `MessageType`

**Xong khi:** test JUnit dùng `SSLSocket` kết nối vào, gửi `ping` nhận `pong`; gửi JSON hỏng nhận `error`; kết nối không TLS bị từ chối; ngắt đột ngột thì session bị xóa đúng một lần.

## Bước 4 – Đăng nhập phía server

1. `security/PasswordHasher` (BCrypt)
2. `repository/UserRepository`
3. `security/LoginAttemptLimiter`, `security/InputValidator`
4. `service/AuthService`
5. `handler/RegisterHandler`, `handler/LoginHandler` (đổi trạng thái sang `AUTHENTICATED`)
6. Chặn mọi loại gói khác khi chưa `AUTHENTICATED`

**Xong khi:** test đăng ký rồi đăng nhập đúng → `login_result` thành công; sai mật khẩu → thất bại và thông báo chung; sai quá 5 lần thì bị khóa tạm; chưa đăng nhập mà gửi tin → `NOT_AUTHENTICATED`; log và DB không có mật khẩu rõ.

## Bước 5 – Client lõi + màn hình Login

1. `config/ClientConfig`, `config/TrustStoreLoader`
2. `net/TcpClient` (connect có timeout + TLS handshake)
3. `net/ServerCertificateValidator` (hostname, hạn dùng, chuỗi tin cậy)
4. `net/PacketSender` (sendLock), `net/ReceiveLoop` (luồng nhận riêng)
5. `service/AuthClient`
6. `state/ConnectionState`, `util/UiThread`
7. `ClientMain` + `ui/controller/LoginController` + `fxml/login.fxml`

**Xong khi:** mở client, đăng ký và đăng nhập được với server thật; sai tên chứng chỉ hoặc chứng chỉ hết hạn thì client từ chối; tắt server thì giao diện không treo mà báo mất kết nối.

> Mốc này là **bản demo đầu tiên**: TLS + đăng nhập chạy end-to-end.

## Bước 6 – Chat 1-1 (kèm Time và Sender)

Server:
1. `repository/ConversationRepository`, `repository/MessageRepository`
2. `service/ConversationService` (mở hội thoại DIRECT), `service/MessageService`
3. `handler/ChatHandler`: lưu DB **thành công rồi mới** gửi cho người nhận; gán `sentAt`
4. Tìm người dùng để bắt đầu chat (danh sách hoặc tìm theo username)

Client:
5. `service/ChatClient`, `state/` (danh sách hội thoại, tin nhắn)
6. `ui/controller/MainController`, `ChatController`, `ui/component/MessageCell` (hiển thị người gửi + giờ)

**Xong khi:** hai client đăng nhập hai tài khoản nhắn qua lại thấy tin tức thì, đúng người gửi và đúng giờ; người nhận offline thì tin vẫn lưu DB.

## Bước 7 – History + cache SQLite ở client

1. Server: `history_request` (`beforeId` / `afterId`, phân trang)
2. Client: `cache/MessageCache` + `cache-schema.sql`, đồng bộ theo `last_message_id`
3. UI: cuộn lên để tải thêm tin cũ; banner offline

**Xong khi:** đóng mở lại client vẫn thấy lịch sử; ngắt mạng vẫn xem được tin cũ từ cache; mở lại mạng chỉ tải phần tin mới, không trùng tin.

## Bước 8 – State (trạng thái)

1. `service/PresenceService`: ONLINE/OFFLINE, gửi `presence` cho người liên quan
2. `message_receipts` + `mark_seen`: SENT → DELIVERED → SEEN
3. UI: chấm xanh online, dấu đã gửi/đã nhận/đã xem

**Xong khi:** tắt một client thì client kia thấy chuyển offline; người nhận mở khung chat thì người gửi thấy "đã xem".

## Bước 9 – Group

1. `service/GroupService` + handler: `create_group`, `add_member`, `remove_member`, `leave_group`
2. Gửi tin nhóm tới tất cả thành viên online, lưu receipt từng người
3. UI: tạo nhóm, danh sách thành viên, tin hệ thống ("An đã tham gia")

**Xong khi:** 3 client trong một nhóm nhắn chung thấy đủ tin; bị xóa khỏi nhóm thì không nhận được nữa; một thành viên lỗi mạng không ảnh hưởng người khác.

## Bước 10 – Emoji

1. Bộ ảnh emoji trong `resources/emoji`, `ui/component/EmojiPicker`
2. Gửi dạng text Unicode (kind `EMOJI` hoặc lẫn trong text); hiển thị đổi ký tự thành ảnh

**Xong khi:** chọn emoji gửi sang client khác hiển thị đúng, trên cả Windows lẫn macOS.

## Bước 11 – File (chia chunk)

1. `storage/FileStorage` (kiểm tra đuôi, dung lượng, tên an toàn), `repository/AttachmentRepository`
2. `service/FileTransferService` + handler `file_begin / file_chunk / file_end / file_download`
3. Client: `service/FileClient`, thanh tiến trình, thư mục `downloads`

**Xong khi:** gửi được file vài chục MB, nhận về đúng nội dung (so sánh hash); file quá lớn hoặc đuôi cấm bị từ chối; không có path traversal qua tên file.

## Bước 12 – Image, Video, Sound

1. `ImageMessageCell`: hiển thị ảnh thu nhỏ, bấm để xem lớn
2. `MediaMessageCell`: phát video và âm thanh bằng `MediaPlayer`

**Xong khi:** gửi ảnh thấy thumbnail; bấm phát được video và âm thanh trong khung chat.

## Bước 13 – Kiểm thử và hoàn thiện

- Chạy bảng test TC01–TC06 trong slide (đúng/sai mật khẩu, chứng chỉ sai tên, chứng chỉ hết hạn, kết nối không TLS, client ngắt đột ngột).
- Test tải: 5 client nhắn liên tục, DB không báo `too many clients`, tin không giật.
- Rà soát log: không có mật khẩu, token, nội dung tin.
- Viết báo cáo, vẽ sơ đồ vào `docs/`, quay video demo.

---

## Gợi ý chia việc nếu làm nhóm

| Người | Phụ trách |
|---|---|
| A | Bước 1–4 (common + server nền + đăng nhập) |
| B | Bước 5 rồi 10, 12 (client lõi, UI, emoji, media) |
| C | Bước 6–9 phần server (chat, history, state, group) |
| Cả nhóm | Bước 11 (file) và 13 (kiểm thử) |

Bước 1 phải xong trước vì mọi người đều dùng chung; từ bước 2 trở đi server và client có thể làm song song nếu thống nhất bảng giao thức ở `ARCHITECTURE.md`.
