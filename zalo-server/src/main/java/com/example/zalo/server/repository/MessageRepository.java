package com.example.zalo.server.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

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
}
