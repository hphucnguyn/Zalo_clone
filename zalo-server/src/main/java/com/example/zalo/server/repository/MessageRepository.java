package com.example.zalo.server.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import com.example.zalo.common.model.MessageDto;

/** JDBC persistence for text messages. */
public class MessageRepository {

    private final DataSource dataSource;

    public MessageRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public long insertText(long conversationId, long senderId, String content, long sentAt) {
        String sql = "INSERT INTO messages (conversation_id, sender_id, message_type, content, sent_at) VALUES (?, ?, 'TEXT', ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, conversationId);
            statement.setLong(2, senderId);
            statement.setString(3, content);
            statement.setLong(4, sentAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("Message insert returned no generated id");
                }
                return keys.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save message", exception);
        }
    }

    /** Loads one cursor page plus one sentinel row so callers can determine whether older messages exist. */
    public List<MessageDto> findPage(long conversationId, long beforeId, long afterId, int limit) {
        boolean loadingNewer = afterId > 0;
        String cursorClause = loadingNewer ? "AND m.id > ?" : beforeId > 0 ? "AND m.id < ?" : "";
        String order = loadingNewer ? "ASC" : "DESC";
        String sql = """
                SELECT m.id, m.conversation_id, m.sender_id, u.display_name, m.content, m.sent_at
                FROM messages m
                JOIN users u ON u.id = m.sender_id
                WHERE m.conversation_id = ?
                """ + cursorClause + " ORDER BY m.id " + order + " LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, conversationId);
            int parameter = 2;
            if (!cursorClause.isEmpty()) {
                statement.setLong(parameter++, loadingNewer ? afterId : beforeId);
            }
            statement.setInt(parameter, limit + 1);
            try (ResultSet result = statement.executeQuery()) {
                List<MessageDto> messages = new ArrayList<>();
                while (result.next()) {
                    messages.add(new MessageDto(
                            result.getLong("id"),
                            result.getLong("conversation_id"),
                            result.getLong("sender_id"),
                            result.getString("display_name"),
                            result.getString("content"),
                            result.getLong("sent_at")));
                }
                return messages;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load message history", exception);
        }
    }

    public OptionalLong findSenderId(long messageId, long conversationId) {
        String sql = "SELECT sender_id FROM messages WHERE id = ? AND conversation_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, messageId);
            statement.setLong(2, conversationId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? OptionalLong.of(result.getLong(1)) : OptionalLong.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load message sender", exception);
        }
    }
}
