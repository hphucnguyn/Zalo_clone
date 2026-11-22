package com.example.zalo.client.cache;

import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.MessageDto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Per-account SQLite replica of chat history. Every operation runs on one background thread. */
public final class MessageCache implements AutoCloseable {

    private final String jdbcUrl;
    private final int maxMessagesPerConversation;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MessageCache(String jdbcUrl, int maxMessagesPerConversation) {
        this.jdbcUrl = jdbcUrl;
        this.maxMessagesPerConversation = maxMessagesPerConversation;
    }

    public static MessageCache open(Path cacheDirectory, String username, int maxMessagesPerConversation) {
        Objects.requireNonNull(cacheDirectory, "cacheDirectory must not be null");
        String safeName = Objects.requireNonNull(username, "username must not be null").replaceAll("[^a-zA-Z0-9_-]", "_");
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create cache directory", exception);
        }
        MessageCache cache = new MessageCache("jdbc:sqlite:" + cacheDirectory.resolve(safeName + ".db").toAbsolutePath(), maxMessagesPerConversation);
        cache.initialize();
        return cache;
    }

    public CompletableFuture<Void> saveConversation(ConversationDto conversation) {
        return submit(() -> {
            try (Connection connection = connect();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO cached_conversations (
                             id, type, name, counterpart_id, counterpart_username,
                             counterpart_display_name, updated_at
                         ) VALUES (?, ?, ?, ?, ?, ?, ?)
                         ON CONFLICT(id) DO UPDATE SET
                             type = excluded.type,
                             name = excluded.name,
                             counterpart_id = excluded.counterpart_id,
                             counterpart_username = excluded.counterpart_username,
                             counterpart_display_name = excluded.counterpart_display_name,
                             updated_at = excluded.updated_at
                         """)) {
                statement.setLong(1, conversation.id());
                statement.setString(2, conversation.type());
                statement.setString(3, conversation.counterpart() == null ? conversation.name() : conversation.counterpart().displayName());
                if (conversation.counterpart() == null) {
                    statement.setNull(4, java.sql.Types.BIGINT);
                    statement.setNull(5, java.sql.Types.VARCHAR);
                    statement.setNull(6, java.sql.Types.VARCHAR);
                } else {
                    statement.setLong(4, conversation.counterpart().id());
                    statement.setString(5, conversation.counterpart().username());
                    statement.setString(6, conversation.counterpart().displayName());
                }
                statement.setLong(7, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<ConversationDto>> loadConversations() {
        return submit(() -> {
            try (Connection connection = connect();
                 PreparedStatement statement = connection.prepareStatement(
                     """
                     SELECT id, type, name, counterpart_id, counterpart_username,
                            counterpart_display_name, updated_at
                     FROM cached_conversations ORDER BY updated_at DESC
                     """)) {
                try (ResultSet result = statement.executeQuery()) {
                    List<ConversationDto> list = new ArrayList<>();
                    while (result.next()) {
                        long id = result.getLong("id");
                        String type = result.getString("type");
                        String name = result.getString("name");
                    com.example.zalo.common.model.UserDto counterpart = null;
                    if ("DIRECT".equalsIgnoreCase(type)) {
                        long counterpartId = result.getLong("counterpart_id");
                        if (result.wasNull()) {
                            counterpartId = -1;
                        }
                        String username = result.getString("counterpart_username");
                        String displayName = result.getString("counterpart_display_name");
                        counterpart = new com.example.zalo.common.model.UserDto(
                                counterpartId,
                                username != null ? username : name,
                                displayName != null ? displayName : name,
                                null,
                                false);
                    }
                        list.add(new ConversationDto(id, type, name, counterpart));
                    }
                    return list;
                }
            } catch (SQLException exception) {
                return List.of();
            }
        });
    }

    public CompletableFuture<Void> saveMessages(List<MessageDto> messages) {
        List<MessageDto> copy = List.copyOf(messages);
        return submit(() -> {
            if (copy.isEmpty()) {
                return null;
            }
            try (Connection connection = connect()) {
                connection.setAutoCommit(false);
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO cached_messages (id, conversation_id, sender_id, sender_name, message_type, content, status, sent_at)
                        VALUES (?, ?, ?, ?, 'TEXT', ?, 'SENT', ?)
                        ON CONFLICT(id) DO NOTHING
                        """);
                     PreparedStatement sync = connection.prepareStatement("""
                        INSERT INTO sync_state (conversation_id, last_message_id) VALUES (?, ?)
                        ON CONFLICT(conversation_id) DO UPDATE SET last_message_id = MAX(last_message_id, excluded.last_message_id)
                        """)) {
                    for (MessageDto message : copy) {
                        insert.setLong(1, message.id());
                        insert.setLong(2, message.conversationId());
                        insert.setLong(3, message.senderId());
                        insert.setString(4, message.senderName());
                        insert.setString(5, message.content());
                        insert.setLong(6, message.sentAt());
                        insert.addBatch();
                        sync.setLong(1, message.conversationId());
                        sync.setLong(2, message.id());
                        sync.addBatch();
                    }
                    insert.executeBatch();
                    sync.executeBatch();
                    trimConversations(connection, copy);
                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
            return null;
        });
    }

    public CompletableFuture<List<MessageDto>> loadRecent(long conversationId, int limit) {
        return submit(() -> {
            try (Connection connection = connect();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT id, conversation_id, sender_id, sender_name, content, sent_at
                         FROM cached_messages WHERE conversation_id = ? ORDER BY id DESC LIMIT ?
                         """)) {
                statement.setLong(1, conversationId);
                statement.setInt(2, Math.max(1, limit));
                try (ResultSet result = statement.executeQuery()) {
                    List<MessageDto> messages = new ArrayList<>();
                    while (result.next()) {
                        messages.add(readMessage(result));
                    }
                    Collections.reverse(messages);
                    return messages;
                }
            }
        });
    }

    public CompletableFuture<Long> lastMessageId(long conversationId) {
        return submit(() -> {
            try (Connection connection = connect();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT last_message_id FROM sync_state WHERE conversation_id = ?")) {
                statement.setLong(1, conversationId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getLong(1) : 0L;
                }
            }
        });
    }

    private void initialize() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            String sql = readSchema();
            for (String fragment : sql.split(";")) {
                if (!fragment.isBlank()) {
                    statement.execute(fragment);
                }
            }
            ensureConversationColumn(connection, "counterpart_id", "INTEGER");
            ensureConversationColumn(connection, "counterpart_username", "TEXT");
            ensureConversationColumn(connection, "counterpart_display_name", "TEXT");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize local message cache", exception);
        }
    }

    private void ensureConversationColumn(Connection connection, String columnName, String columnType) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(cached_conversations)");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (columnName.equalsIgnoreCase(result.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE cached_conversations ADD COLUMN " + columnName + " " + columnType);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static String readSchema() {
        try (InputStream input = MessageCache.class.getClassLoader().getResourceAsStream("cache-schema.sql")) {
            if (input == null) {
                throw new IllegalStateException("Missing cache-schema.sql");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read cache schema", exception);
        }
    }

    private static MessageDto readMessage(ResultSet result) throws SQLException {
        return new MessageDto(
                result.getLong("id"), result.getLong("conversation_id"), result.getLong("sender_id"),
                result.getString("sender_name"), result.getString("content"), result.getLong("sent_at"));
    }

    private void trimConversations(Connection connection, List<MessageDto> messages) throws SQLException {
        for (long conversationId : messages.stream().mapToLong(MessageDto::conversationId).distinct().toArray()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM cached_messages
                    WHERE conversation_id = ? AND id NOT IN (
                        SELECT id FROM cached_messages WHERE conversation_id = ? ORDER BY id DESC LIMIT ?
                    )
                    """)) {
                statement.setLong(1, conversationId);
                statement.setLong(2, conversationId);
                statement.setInt(3, maxMessagesPerConversation);
                statement.executeUpdate();
            }
        }
    }

    private <T> CompletableFuture<T> submit(SqlTask<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.run();
            } catch (SQLException exception) {
                throw new IllegalStateException("Local message cache operation failed", exception);
            }
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface SqlTask<T> {
        T run() throws SQLException;
    }
}
