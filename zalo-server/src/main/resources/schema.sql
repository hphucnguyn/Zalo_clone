-- Schema PostgreSQL cho Zalo Clone (chay lai nhieu lan khong loi: IF NOT EXISTS)
-- Thoi gian luu dang epoch millis (BIGINT) de khop voi giao thuc JSON.

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,          -- BCrypt, KHONG luu plain text
  display_name  VARCHAR(100) NOT NULL,
  avatar_path   TEXT,
  created_at    BIGINT NOT NULL,
  last_seen_at  BIGINT
);

CREATE TABLE IF NOT EXISTS friendships (
  user_id    BIGINT NOT NULL REFERENCES users(id),
  friend_id  BIGINT NOT NULL REFERENCES users(id),
  created_at BIGINT NOT NULL,
  PRIMARY KEY (user_id, friend_id)
);

-- type = 'DIRECT' (1-1) hoac 'GROUP'
CREATE TABLE IF NOT EXISTS conversations (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type       VARCHAR(10) NOT NULL CHECK (type IN ('DIRECT','GROUP')),
  name       VARCHAR(100),                      -- ten nhom (null neu DIRECT)
  owner_id   BIGINT REFERENCES users(id),
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS conversation_members (
  conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id         BIGINT NOT NULL REFERENCES users(id),
  role            VARCHAR(10) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('OWNER','ADMIN','MEMBER')),
  joined_at       BIGINT NOT NULL,
  PRIMARY KEY (conversation_id, user_id)
);

-- attachments dat TRUOC messages vi messages tham chieu toi no
CREATE TABLE IF NOT EXISTS attachments (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  file_name   VARCHAR(255) NOT NULL,
  stored_path TEXT NOT NULL,
  mime_type   VARCHAR(100),
  size_bytes  BIGINT NOT NULL,
  duration_ms BIGINT,                           -- video / sound
  uploaded_by BIGINT NOT NULL REFERENCES users(id),
  uploaded_at BIGINT NOT NULL
);

-- message_type: TEXT | EMOJI | FILE | IMAGE | VIDEO | SOUND | SYSTEM
-- status: SENT | DELIVERED | SEEN
CREATE TABLE IF NOT EXISTS messages (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id       BIGINT NOT NULL REFERENCES users(id),
  message_type    VARCHAR(10) NOT NULL,
  content         TEXT,
  attachment_id   BIGINT REFERENCES attachments(id),
  status          VARCHAR(10) NOT NULL DEFAULT 'SENT',
  sent_at         BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_msg_conv_id ON messages(conversation_id, id DESC);

-- da nhan / da xem cua tung nguoi (dung cho nhom)
CREATE TABLE IF NOT EXISTS message_receipts (
  message_id   BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id      BIGINT NOT NULL REFERENCES users(id),
  delivered_at BIGINT,
  seen_at      BIGINT,
  PRIMARY KEY (message_id, user_id)
);

-- Phone lookup and friendship requests
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_number ON users (phone_number) WHERE phone_number IS NOT NULL;
CREATE TABLE IF NOT EXISTS friend_requests (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  receiver_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
  created_at BIGINT NOT NULL,
  responded_at BIGINT,
  CHECK (sender_id <> receiver_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_friend_requests_pending_pair ON friend_requests (LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id)) WHERE status = 'PENDING';
