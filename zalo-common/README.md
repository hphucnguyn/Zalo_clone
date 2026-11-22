# Zalo Common Module (`zalo-common`)

Module thư viện dùng chung cho cả **Zalo Server** (`zalo-server`) và **Zalo Client** (`zalo-client`). Chứa toàn bộ định nghĩa về **giao thức truyền thông (Protocol Contract)**, **các đối tượng truyền tải dữ liệu (DTO Model)** và **các tiện ích (Utilities)**.

---

## 1. Vai trò của Module

Trong kiến trúc Secure TCP Chat, `zalo-common` đóng vai trò là "bản hợp đồng" giao tiếp duy nhất giữa Client và Server:
- **Đảm bảo tính nhất quán:** Cả hai đầu Client và Server đều biên dịch chung một định dạng gói tin (`Packet`), chung danh sách mã lệnh (`MessageType`), mã lỗi (`ErrorCode`) và cấu trúc dữ liệu (`DTO`).
- **Độc lập và gọn nhẹ:** Chỉ phụ thuộc vào các thư viện chuẩn về JSON (Jackson) và Logging (SLF4J). Tuyệt đối **không** phụ thuộc vào JDBC, cơ sở dữ liệu hay giao diện người dùng (JavaFX).

---

## 2. Cấu trúc thư mục

```
zalo-common/
├── pom.xml
└── src/
    ├── main/java/com/example/zalo/common/
    │   ├── model/                  # Data Transfer Objects (DTO)
    │   │   ├── ConversationDto.java   # Thông tin hội thoại (1-1 hoặc Nhóm)
    │   │   ├── FriendRequestDto.java  # Thông tin lời mời kết bạn
    │   │   ├── MessageDto.java        # Nội dung tin nhắn & metadata
    │   │   ├── MessageStatus.java     # Trạng thái tin (SENT, DELIVERED, SEEN)
    │   │   └── UserDto.java           # Thông tin người dùng (id, username, phone, display_name...)
    │   ├── protocol/               # Giao thức truyền thông
    │   │   ├── ErrorCode.java         # Danh sách mã lỗi chuẩn
    │   │   ├── MessageType.java       # Enum định danh mọi loại gói tin
    │   │   ├── Packet.java            # Record biểu diễn một gói tin
    │   │   └── ProtocolConstants.java # Hằng số giao thức (Port, Buffer, Charset)
    │   └── util/                   # Tiện ích chung
    │       ├── JsonUtil.java          # Wrapper serialize/deserialize Jackson chuẩn UTF-8
    │       └── TimeUtil.java          # Tiện ích định dạng thời gian epoch millis
    └── test/java/com/example/zalo/common/
        └── util/
            └── JsonUtilTest.java      # Unit test kiểm tra tính toàn vẹn gói tin JSON
```

---

## 3. Quy chuẩn Giao thức (Protocol Specification)

### 3.1 Định dạng Gói tin (`Packet`)
Giao thức tầng ứng dụng hoạt động trên TCP Socket có bọc mã hoá TLS. Dữ liệu trao đổi theo cơ chế **Newline-Delimited JSON (NDJSON)**:
- Mỗi gói tin là **một dòng JSON hoàn chỉnh**, kết thúc bằng ký tự xuống dòng `\n`.
- Kích thước tối đa của một dòng: **64 KB** (`ProtocolConstants.MAX_LINE_BYTES = 65536`).
- Bảng mã: **UTF-8** (`StandardCharsets.UTF_8`).

Cấu trúc JSON chuẩn:
```json
{
  "type": "<message_type_wire_name>",
  "requestId": "<uuid_hoặc_null>",
  "data": { ... }
}
```

- **`type` (MessageType):** Tên định danh của loại thông điệp (bắt buộc).
- **`requestId` (String):** Chuỗi định danh duy nhất (UUID) của yêu cầu. Server sẽ phản hồi lại đúng `requestId` này để Client ánh xạ vào `CompletableFuture` tương ứng. Gói tin bất đồng bộ từ Server gửi xuống (như tin nhắn đến, presence) có thể để `requestId` là `null`.
- **`data` (JsonNode / Object):** Dữ liệu chi tiết đính kèm theo từng loại thông điệp.

---

### 3.2 Danh mục `MessageType`

Giao thức hỗ trợ 34 loại thông điệp, chia làm các nhóm nghiệp vụ:

| Nhóm | Wire Name | Hướng | Ý nghĩa |
|---|---|:---:|---|
| **Auth** | `register` | C ➔ S | Yêu cầu đăng ký tài khoản mới |
| | `register_result` | S ➔ C | Kết quả đăng ký (thành công/thất bại) |
| | `login` | C ➔ S | Yêu cầu đăng nhập |
| | `login_result` | S ➔ C | Kết quả đăng nhập (trả về `UserDto`) |
| | `disconnect` | C ➔ S | Báo ngắt kết nối chủ động |
| **Direct Chat** | `open_direct` | C ➔ S | Mở hoặc tạo hội thoại 1-1 với username |
| | `direct_opened` | S ➔ C | Trả về thông tin `ConversationDto` |
| **Friend System** | `search_user_by_phone` | C ➔ S | Tìm kiếm người dùng bằng số điện thoại |
| | `user_search_result` | S ➔ C | Kết quả tìm kiếm (`found: true/false`, `UserDto`) |
| | `send_friend_request` | C ➔ S | Gửi yêu cầu kết bạn tới `userId` |
| | `friend_request_result` | S ➔ C | Kết quả gửi lời mời kết bạn |
| | `list_friends` | C ➔ S | Lấy danh sách bạn bè hiện tại |
| | `friends_list` | S ➔ C | Trả về danh sách `users: [...]` |
| | `list_friend_requests` | C ➔ S | Lấy danh sách lời mời kết bạn đang chờ |
| | `friend_requests` | S ➔ C | Trả về danh sách `requests: [...]` |
| | `respond_friend_request`| C ➔ S | Phản hồi lời mời kết bạn (chấp nhận / từ chối) |
| | `friend_request_update` | S ➔ C | Thông báo realtime khi có cập nhật lời mời kết bạn |
| **Group Chat** | `create_group` | C ➔ S | Tạo nhóm chat mới kèm danh sách thành viên |
| | `group_created` | S ➔ C | Trả về thông tin nhóm vừa tạo |
| | `add_member` | C ➔ S | Thêm thành viên vào nhóm |
| | `remove_member` | C ➔ S | Xoá thành viên khỏi nhóm |
| | `leave_group` | C ➔ S | Rời khỏi nhóm |
| | `conversation_updated` | S ➔ C | Báo thay đổi thành viên/thông tin nhóm |
| **Messaging** | `send_message` | C ➔ S | Gửi tin nhắn (`conversationId`, `content`, `kind`) |
| | `message` | S ➔ C | Phân phối tin nhắn tới người nhận (realtime) |
| | `history_request` | C ➔ S | Yêu cầu tải lịch sử (`beforeId`, `afterId`, `limit`) |
| | `history_response` | S ➔ C | Trả về trang lịch sử (`messages`, `hasMore`) |
| **State & Receipts**| `presence` | S ➔ C | Báo trạng thái online/offline của người dùng |
| | `mark_seen` | C ➔ S | Đánh dấu đã đọc tin nhắn trong hội thoại |
| | `message_status` | S ➔ C | Cập nhật trạng thái tin nhắn (DELIVERED, SEEN) |
| **Keep-Alive** | `ping` | C ➔ S | Kiểm tra đường truyền (heartbeat) |
| | `pong` | S ➔ C | Phản hồi kiểm tra đường truyền |
| **System & Error**| `system` | S ➔ C | Thông báo hệ thống (ví dụ: "A đã tham gia nhóm") |
| | `error` | S ➔ C | Báo lỗi (`code`, `message`) |

---

### 3.3 Danh mục mã lỗi (`ErrorCode`)

Khi có sự cố hoặc vi phạm giao thức, Server trả về gói `type: "error"` với một trong các mã lỗi:
- `INVALID_JSON`: JSON bị sai cú pháp hoặc dòng trống.
- `NOT_AUTHENTICATED`: Phiên làm việc chưa đăng nhập thành công nhưng đã gửi gói tin nghiệp vụ.
- `UNKNOWN_TYPE`: Loại thông điệp không được hệ thống hỗ trợ.
- `VALIDATION_FAILED`: Dữ liệu gửi lên không vượt qua kiểm tra tính hợp lệ (ví dụ: username quá ngắn, mật khẩu yếu).
- `RATE_LIMITED`: Bị chặn do vi phạm giới hạn tần suất hoặc nhập sai mật khẩu quá 5 lần.
- `NOT_FOUND`: Không tìm thấy tài nguyên (người dùng, hội thoại, tin nhắn).
- `ALREADY_EXISTS`: Tài nguyên đã tồn tại (ví dụ: username hoặc số điện thoại đã đăng ký).
- `FORBIDDEN`: Không có quyền thực hiện thao tác (ví dụ: không phải admin nhóm).
- `INTERNAL_ERROR`: Lỗi nội bộ không lường trước ở phía server.

---

## 4. Các Data Models (DTOs)

Các DTO được định nghĩa bằng Java `record`, bất biến (immutable), tự động tương thích với Jackson:

1. **`UserDto`:**
   - `id`: Mã định danh người dùng (Long).
   - `username`: Tên đăng nhập.
   - `displayName`: Tên hiển thị người dùng thấy.
   - `phoneNumber`: Số điện thoại định danh.
   - `avatarPath`: Đường dẫn ảnh đại diện.
   - `lastSeenAt`: Thời điểm online gần nhất (epoch millis).

2. **`MessageDto`:**
   - `id`: Mã định danh tin nhắn trong cơ sở dữ liệu.
   - `conversationId`: Mã hội thoại chứa tin nhắn.
   - `senderId`: ID người gửi.
   - `senderName`: Tên hiển thị người gửi.
   - `messageType`: Loại tin nhắn (`TEXT`, `EMOJI`, `FILE`, `IMAGE`, `VIDEO`, `SOUND`, `SYSTEM`).
   - `content`: Nội dung văn bản hoặc tên file.
   - `attachmentId`: Mã file đính kèm (nếu có).
   - `status`: Trạng thái tin (`SENT`, `DELIVERED`, `SEEN`).
   - `sentAt`: Thời điểm gửi (epoch millis do Server gán).

3. **`ConversationDto`:**
   - `id`: Mã hội thoại.
   - `type`: `DIRECT` (chat 1-1) hoặc `GROUP` (chat nhóm).
   - `name`: Tên nhóm (với chat nhóm).
   - `counterpart`: Thông tin người đối thoại (`UserDto`, chỉ có với chat 1-1).
   - `memberCount`: Số lượng thành viên.
   - `createdAt`: Thời gian tạo hội thoại.

4. **`FriendRequestDto`:**
   - `id`: Mã lời mời.
   - `sender`: Thông tin người gửi lời mời (`UserDto`).
   - `receiver`: Thông tin người nhận (`UserDto`).
   - `status`: Trạng thái (`PENDING`, `ACCEPTED`, `REJECTED`).
   - `createdAt`: Thời gian gửi lời mời.

---

## 5. Thư viện tiện ích (`util`)

### `JsonUtil`
- Cung cấp một `ObjectMapper` dùng chung được tối ưu hóa, đăng ký module `JavaTimeModule` để xử lý thời gian.
- **Quy tắc an toàn giao thức:** Khi parse hoặc ghi JSON, `JsonUtil` xử lý gói gọn trong một dòng, ngăn chặn các ký tự `\r` hoặc `\n` trong nội dung tin nhắn làm rách dòng NDJSON (Jackson tự động escape `\n` thành `\n` ký tự chuỗi an toàn).
- Hỗ trợ các hàm tiện lợi: `toJson(Object)`, `fromJson(String, Class<T>)`, `valueToTree(Object)`, `treeToValue(JsonNode, Class<T>)`.

### `TimeUtil`
- Xử lý mốc thời gian dạng `epoch millis` (UTC).
- Chuyển đổi linh hoạt sang định dạng hiển thị giờ/ngày (`HH:mm`, `dd/MM/yyyy`) cho Client.

---

## 6. Hướng dẫn kiểm thử và mở rộng

### Chạy Unit Test
```bash
# Đảm bảo JAVA_HOME trỏ tới JDK 17+
mvn test -pl zalo-common
```

### Quy tắc khi mở rộng giao thức
1. Nếu thêm `MessageType` mới: Cập nhật enum trong `MessageType.java`, đồng thời đăng ký handler tương ứng trong Server `PacketDispatcher` và xử lý trong Client `ChatClient` / `TcpClient`.
2. Giữ vững tính tương thích ngược: Không tự ý xoá hoặc đổi tên các wire name đã có.
3. Không đặt dữ liệu binary trực tiếp vào Packet: Dữ liệu lớn hoặc nhị phân phải được chia chunk và mã hóa Base64 trước khi đóng gói.
