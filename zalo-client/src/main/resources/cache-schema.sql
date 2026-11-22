-- SQLite CACHE o may client. Day chi la ban sao de xem offline, nguon su that la server.
CREATE TABLE IF NOT EXISTS cached_conversations (
  id         INTEGER PRIMARY KEY,               -- id cua server
  type       TEXT NOT NULL,                     -- DIRECT | GROUP
  name       TEXT,
  counterpart_id INTEGER,
  counterpart_username TEXT,
  counterpart_display_name TEXT,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS cached_messages (
  id              INTEGER PRIMARY KEY,          -- id cua server (chong trung khi dong bo lai)
  conversation_id INTEGER NOT NULL,
  sender_id       INTEGER NOT NULL,
  sender_name     TEXT NOT NULL,
  message_type    TEXT NOT NULL,
  content         TEXT,
  attachment_json TEXT,                         -- metadata file (ten, kich thuoc, duong dan da tai)
  status          TEXT NOT NULL,
  sent_at         INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_cmsg_conv ON cached_messages(conversation_id, id DESC);

-- moc dong bo: lan sau chi xin tin co id > last_message_id
CREATE TABLE IF NOT EXISTS sync_state (
  conversation_id INTEGER PRIMARY KEY,
  last_message_id INTEGER NOT NULL DEFAULT 0
);
