# Zalo Clone (Java – Secure TCP Chat)

Ứng dụng chat desktop kiểu Zalo cho môn Lập trình mạng.
Java 17, TLS (`SSLSocket`), JavaFX, PostgreSQL (Docker) ở server, SQLite cache ở client.

- Kiến trúc, thư viện, giao thức: [`ARCHITECTURE.md`](ARCHITECTURE.md)
- Thứ tự code từng bước, tiêu chí nghiệm thu: [`docs/ROADMAP.md`](docs/ROADMAP.md)

## Tính năng theo tiêu chí

Chat 1-1, nhóm, gửi file, emoji, lịch sử, ảnh, video, âm thanh, trạng thái (online, đã xem), thời gian, người gửi.

## Sơ đồ

```
Client JavaFX ──(Socket + TLS 1.2/1.3, JSON mỗi dòng)──► Java Server (đa luồng) ──JDBC/HikariCP──► PostgreSQL (Docker)
   └─ SQLite cache (xem lịch sử khi mất mạng)
```

Client không bao giờ kết nối trực tiếp tới PostgreSQL.

## Yêu cầu cài đặt

| Công cụ | Phiên bản |
|---|---|
| JDK | 17 trở lên (`java -version`) |
| Maven | 3.9 trở lên (`mvn -v`) |
| Docker Desktop | bản mới (`docker compose version`) |
| IDE | IntelliJ IDEA (mở thư mục gốc, Import Maven project) |

## Cài đặt và chạy

1. Tạo file môi trường:
   ```bash
   cp .env.example .env        # sửa mật khẩu trong .env
   ```
2. Export biến môi trường (cùng giá trị với `.env`):
   - Linux/macOS: `export DB_USER=zalo_app DB_PASSWORD='...' CHAT_CERT_PASSWORD='...'`
   - Windows (CMD): `set DB_USER=zalo_app` rồi `set DB_PASSWORD=...` và `set CHAT_CERT_PASSWORD=...`
3. Bật PostgreSQL:
   ```bash
   docker compose up -d
   docker compose ps           # đợi STATUS = healthy
   ```
4. Tạo chứng chỉ TLS (tạo `server.p12` cho server, `server.crt` cho client):
   ```bash
   ./scripts/gen-certs.sh localhost        # Windows: scripts\gen-certs.bat
   ```
5. Build:
   ```bash
   mvn clean package
   ```
6. Chạy (khi đã có code, xem lộ trình):
   ```bash
   java -jar zalo-server/target/zalo-server.jar
   mvn -pl zalo-client javafx:run          # mở nhiều cửa sổ để test nhiều client
   ```

Chạy client trên máy khác trong LAN: sửa `server.host` và `tls.expectedHost` trong `client.properties`, và tạo chứng chỉ với đúng tên host hoặc IP (`./scripts/gen-certs.sh <host>`).

## Cấu trúc dự án

```
ZaloClone/
├── pom.xml                  parent POM (multi-module)
├── docker-compose.yml       PostgreSQL
├── .env.example             mẫu biến môi trường
├── scripts/                 gen-certs.sh / gen-certs.bat
├── docs/                    ROADMAP, sơ đồ, báo cáo
├── zalo-common/             giao thức + DTO dùng chung
├── zalo-server/             TLS server, handler, service, repository
└── zalo-client/             JavaFX UI, kết nối TLS, cache SQLite
```

Chi tiết từng package: mục 5 của `ARCHITECTURE.md`.

## File cấu hình chính

| File | Nội dung |
|---|---|
| `zalo-server/src/main/resources/server.properties` | cổng, TLS, DB pool, giới hạn dung lượng |
| `zalo-server/src/main/resources/schema.sql` | bảng PostgreSQL |
| `zalo-client/src/main/resources/client.properties` | địa chỉ server, truststore, cache |
| `zalo-client/src/main/resources/cache-schema.sql` | bảng SQLite cache |
| `.env` (không commit) | `DB_USER`, `DB_PASSWORD`, `CHAT_CERT_PASSWORD` |

## Quy tắc bảo mật (không được vi phạm)

- Không commit `*.p12`, `*.key`, `.env` (đã chặn trong `.gitignore`). Client chỉ cần `server.crt`.
- Client không có thông tin DB; mọi truy vấn JDBC nằm ở server.
- Không bao giờ log mật khẩu, token hay nội dung tin nhắn.
- Mật khẩu người dùng lưu bằng BCrypt.

## Lỗi thường gặp

| Lỗi | Nguyên nhân / cách xử lý |
|---|---|
| `Connection refused` khi client kết nối | Server chưa chạy, sai IP hoặc cổng 5000, hoặc firewall chặn |
| `SocketTimeoutException` | Sai IP, khác mạng, hoặc firewall |
| `SSLHandshakeException` / hostname mismatch | Tên trong chứng chỉ không khớp `tls.expectedHost`; tạo lại chứng chỉ đúng host |
| `FATAL: sorry, too many clients already` | Đang mở kết nối JDBC thủ công; phải dùng HikariCP |
| `password authentication failed` (DB) | `DB_USER`/`DB_PASSWORD` không khớp với `.env` lúc tạo container. Xóa volume (`docker compose down -v`) rồi tạo lại |
| Server không nạp được keystore | Sai `CHAT_CERT_PASSWORD` hoặc chưa chạy `gen-certs` |
