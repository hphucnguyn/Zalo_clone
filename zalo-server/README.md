# Zalo Server Module (`zalo-server`)

Máy chủ trò chuyện bảo mật đa luồng (**Secure TCP Chat Server**) viết bằng Java thuần (không dùng framework nặng như Spring hay Netty), sử dụng **TLS Socket** (`SSLServerSocket`), **HikariCP** kết nối tới cơ sở dữ liệu **PostgreSQL** (chạy Docker) và quản lý phiên người dùng thời gian thực.

---

## 1. Kiến trúc Tổng thể & Phân tầng (4 Tiers)

Server được thiết kế theo mô hình 4 tầng phân lập rõ ràng, tuân thủ nguyên tắc: *Tầng trên chỉ gọi tầng dưới, không nhảy cóc*:

```
                 [ Client JavaFX ]
                         │ TLS (Port 5000)
                         ▼
┌─────────────────────────────────────────────────────────┐
│ TẦNG 1: NETWORKING (`com.example.zalo.server.net`)      │
│ • ChatServer (Accept Loop, SSLServerSocket)             │
│ • ClientHandler (Đọc từng dòng NDJSON, kiểm soát vòng đời)│
│ • PacketWriter (Ghi gói tin với ReentrantLock)          │
└────────────────────────┬────────────────────────────────┘
                         │ Packet
                         ▼
┌─────────────────────────────────────────────────────────┐
│ TẦNG 2: DISPATCHER & HANDLERS (`...server.handler`)     │
│ • PacketDispatcher (Định tuyến theo MessageType)        │
│ • LoginHandler, RegisterHandler, ChatHandler            │
│ • FriendHandler, GroupHandler, HistoryHandler           │
│ • MarkSeenHandler, PingHandler                          │
└────────────────────────┬────────────────────────────────┘
                         │ DTO / Parameters
                         ▼
┌─────────────────────────────────────────────────────────┐
│ TẦNG 3: DOMAIN SERVICES (`...server.service`)           │
│ • AuthService (Xác thực, bảo vệ brute-force)            │
│ • ConversationService (Tạo/tìm hội thoại 1-1)          │
│ • GroupService (Quản lý nhóm, phân quyền)               │
│ • MessageService (Lưu tin nhắn, cập nhật receipts)      │
│ • PresenceService (Quản lý trạng thái online/offline)   │
└────────────────────────┬────────────────────────────────┘
                         │ JDBC Connection (HikariCP)
                         ▼
┌─────────────────────────────────────────────────────────┐
│ TẦNG 4: REPOSITORY & STORAGE (`...server.repository`)   │
│ • UserRepository, ConversationRepository                │
│ • MessageRepository, ReceiptRepository, FriendRepository│
│ • Database (HikariDataSource quản lý pool kết nối)      │
└─────────────────────────────────────────────────────────┘
                         │ TCP: 5433 -> 5432
                         ▼
             [ PostgreSQL 16 (Docker) ]
```

---

## 2. Mô hình Đa luồng & Quản lý Phiên (Concurrency Model)

1. **Accept Loop riêng biệt:**
   - Một thread duy nhất trong `acceptExecutor` liên tục lắng nghe kết nối từ `SSLServerSocket.accept()`.
   - Mỗi kết nối mới được bàn giao ngay cho `clientExecutor` (Thread Pool cấu hình mặc định tối đa 500 threads).
2. **Quản lý Phiên (`ClientSession` & `SessionRegistry`):**
   - Mỗi client kết nối được đại diện bởi một đối tượng `ClientSession` gồm: socket, trạng thái kết nối (`ClientState`), thông tin `UserDto` (khi đã login) và một khóa `ReentrantLock sendLock`.
   - `SessionRegistry` lưu trữ danh sách phiên bằng `ConcurrentHashMap`. Cho phép tìm kiếm phiên theo `sessionId` hoặc `userId`.
   - **Chống ghi xen kẽ byte (Interleaving bytes):** Nhiều thread có thể đồng thời gửi tin nhắn cho cùng một client (ví dụ: tin nhắn đến, thông báo kết bạn, presence). Mọi thao tác ghi ra socket đều phải giữ `sendLock` của session đó trong `PacketWriter`.
3. **State Machine nghiêm ngặt:**
   ```
   DISCONNECTED ──► TCP_CONNECTED ──► TLS_READY ──► AUTHENTICATED ──► CHATTING ──► CLOSED
   ```
   - Khi chưa đạt trạng thái `AUTHENTICATED`, Server lập tức từ chối mọi gói tin nghiệp vụ (chỉ chấp nhận `register`, `login`, `ping`).

---

## 3. Cơ sở Dữ liệu & Quản trị Kết nối (PostgreSQL + HikariCP)

### 3.1 Quy tắc truy cập Database
- **Tuyệt đối cấm** mở kết nối thủ công bằng `DriverManager.getConnection()`.
- Mọi repository nhận vào `HikariDataSource` từ `Database.java`. Mỗi truy vấn mượn 1 connection từ pool bằng cú pháp `try-with-resources` và trả lại ngay:
  ```java
  try (Connection connection = dataSource.getConnection();
       PreparedStatement stmt = connection.prepareStatement(...)) {
      // Thực thi và kết thúc ngay
  }
  ```
- **Kích thước pool:** Cấu hình `db.pool.maxSize=10` (nhỏ hơn nhiều so với `max_connections=50` của PostgreSQL) đảm bảo dù có 500 client kết nối cùng lúc thì server vẫn chạy ổn định, không bị lỗi `FATAL: sorry, too many clients already`.

### 3.2 Tự động khởi tạo Schema (`schema.sql`)
Khi Server khởi động, lớp `Database.java` tự động đọc file `schema.sql` trong classpath và thực thi. Toàn bộ câu lệnh đều sử dụng `CREATE TABLE IF NOT EXISTS` và `ADD COLUMN IF NOT EXISTS`, đảm bảo an toàn khi chạy lại nhiều lần:
- `users`: Thông tin tài khoản, mật khẩu băm BCrypt, số điện thoại, avatar, trạng thái online.
- `friendships`: Quan hệ bạn bè 2 chiều.
- `friend_requests`: Lời mời kết bạn với trạng thái `PENDING`, `ACCEPTED`, `REJECTED`.
- `conversations`: Hội thoại `DIRECT` (1-1) hoặc `GROUP` (nhóm).
- `conversation_members`: Danh sách thành viên kèm vai trò (`OWNER`, `ADMIN`, `MEMBER`).
- `messages`: Lưu trữ tin nhắn, phân loại (`TEXT`, `FILE`, `IMAGE`...), khóa ngoại tới hội thoại và người gửi.
- `message_receipts`: Lưu mốc thời gian nhận (`delivered_at`) và đã đọc (`seen_at`) của từng thành viên.
- `attachments`: Metadata file đính kèm lưu trên đĩa.

---

## 4. Bảo mật & An toàn Thông tin

1. **Mã hoá đường truyền với TLS 1.2 / TLS 1.3:**
   - Server sử dụng Keystore chuẩn **PKCS12** (`certificates/server.p12`).
   - Mật khẩu Keystore được đọc từ biến môi trường `CHAT_CERT_PASSWORD` (tuyệt đối không hardcode trong code hay file properties).
   - Tự động nạp vào `SSLContext` và gán cho `SSLServerSocket`.
2. **Lưu trữ mật khẩu an toàn:**
   - Sử dụng thuật toán băm **BCrypt** với độ phức tạp `rounds = 12` (`PasswordHasher`).
   - Mật khẩu gốc không bao giờ được lưu trữ hay in ra file log.
3. **Chống tấn công vét cạn (Brute-force Protection):**
   - Lớp `LoginAttemptLimiter` theo dõi số lần đăng nhập sai theo từng username.
   - Đăng nhập sai quá 5 lần (`auth.maxLoginAttempts`) sẽ bị khoá tài khoản trong 60 giây (`auth.lockSeconds`).
4. **Kiểm tra tính hợp lệ dữ liệu (`InputValidator`):**
   - Username: 3 - 30 ký tự (chữ cái, số, gạch dưới).
   - Password: tối thiểu 6 ký tự.
   - Phone number: định dạng số điện thoại hợp lệ (9 - 11 chữ số).

---

## 5. Cấu hình (`server.properties` & Biến môi trường)

File cấu hình chính: `zalo-server/src/main/resources/server.properties`

```properties
# ===== Network =====
server.host=0.0.0.0
server.port=5000
server.backlog=100
server.maxClients=500
server.readTimeoutSeconds=120

# ===== TLS =====
tls.keystore.path=certificates/server.p12
tls.keystore.type=PKCS12
tls.keystore.passwordEnv=CHAT_CERT_PASSWORD
tls.protocols=TLSv1.3,TLSv1.2
tls.needClientAuth=false

# ===== Protocol =====
protocol.maxLineBytes=65536
protocol.charset=UTF-8
protocol.fileChunkBytes=32768

# ===== Database (PostgreSQL trong Docker) =====
db.url=jdbc:postgresql://localhost:5433/zalo
db.user.env=DB_USER
db.password.env=DB_PASSWORD
db.schema=schema.sql

# ===== HikariCP Pool =====
db.pool.maxSize=10
db.pool.minIdle=2
db.pool.connectionTimeoutMs=3000
db.pool.idleTimeoutMs=300000
db.pool.maxLifetimeMs=1800000
```

> **Biến môi trường bắt buộc:**
> - `DB_USER`: Tên tài khoản PostgreSQL (khớp với `.env` của docker-compose).
> - `DB_PASSWORD`: Mật khẩu tài khoản PostgreSQL.
> - `CHAT_CERT_PASSWORD`: Mật khẩu bảo vệ file keystore `server.p12`.

---

## 6. Hướng dẫn Biên dịch & Vận hành

### 6.1 Yêu cầu chuẩn bị
- **JDK:** Phiên bản 17 trở lên (`java -version`).
- **PostgreSQL:** Đang chạy trên cổng `5433` qua Docker Compose.
- **Chứng chỉ TLS:** Đã tạo file `certificates/server.p12` bằng script `gen-certs`.

### 6.2 Đóng gói (Build Fat Jar)
Server sử dụng `maven-shade-plugin` để đóng gói toàn bộ thư viện phụ thuộc thành 1 file jar duy nhất:
```bash
# Từ thư mục gốc dự án
mvn clean package -pl zalo-server -am
```
File thực thi đầu ra: `zalo-server/target/zalo-server.jar`.

### 6.3 Chạy Server

#### Cách 1: Sử dụng Script tự động nạp `.env` (Khuyến nghị)
- **Trên Windows (PowerShell):**
  ```powershell
  .\scripts\run-server.ps1
  ```
- **Trên Windows (Command Prompt):**
  ```cmd
  scripts\run-server.bat
  ```

#### Cách 2: Chạy trực tiếp qua lệnh Java (Thủ công)
- **Trên Windows (PowerShell):**
  ```powershell
  $env:DB_USER = "zalo_app"
  $env:DB_PASSWORD = "DoiMatKhauNay"
  $env:CHAT_CERT_PASSWORD = "DoiMatKhauKhac"
  java -jar zalo-server\target\zalo-server.jar
  ```
- **Trên Linux / macOS:**
  ```bash
  export DB_USER=zalo_app
  export DB_PASSWORD=DoiMatKhauNay
  export CHAT_CERT_PASSWORD=DoiMatKhauKhac
  java -jar zalo-server/target/zalo-server.jar
  ```

### 6.4 Dấu hiệu khởi động thành công
Khi khởi động thành công, màn hình console sẽ hiển thị các dòng log info:
```text
[main] INFO com.example.zalo.server.config.Database - Initializing HikariCP connection pool to jdbc:postgresql://localhost:5433/zalo
[main] INFO com.example.zalo.server.config.Database - Database schema is up to date.
[main] INFO com.example.zalo.server.ServerMain - DB connected, schema ready
[main] INFO com.example.zalo.server.ServerMain - TLS context loaded
[main] INFO com.example.zalo.server.ServerMain - TLS chat server listening on 0.0.0.0:5000
```

---

## 7. Nhật ký hoạt động (Logging)

- Cấu hình qua `zalo-server/src/main/resources/logback.xml`.
- Log vừa in ra console vừa tự động ghi ra file xoay vòng theo ngày tại: `logs/server.log` (và `logs/server.yyyy-MM-dd.i.log`).
- Quy tắc bảo mật: Mọi dữ liệu nhạy cảm (mật khẩu người dùng, token xác thực, nội dung tin nhắn) đều **không** được phép log ở mức INFO/WARN/ERROR.
