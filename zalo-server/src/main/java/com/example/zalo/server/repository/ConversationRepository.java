package com.example.zalo.server.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.OptionalLong;

/** JDBC persistence for direct conversations and their memberships. */
public class ConversationRepository {

    private final DataSource dataSource;

    public ConversationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public long findOrCreateDirect(long firstUserId, long secondUserId, long createdAt) {
        if (firstUserId == secondUserId) {
            throw new IllegalArgumentException("A direct conversation requires two different users");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                OptionalLong existing = findDirect(connection, firstUserId, secondUserId);
                long conversationId = existing.isPresent()
                        ? existing.getAsLong()
                        : createDirect(connection, firstUserId, secondUserId, createdAt);
                connection.commit();
                return conversationId;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not open direct conversation", exception);
        }
    }

    public boolean isMember(long conversationId, long userId) {
        String sql = "SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, conversationId);
            statement.setLong(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not verify conversation membership", exception);
        }
    }

    public long[] memberIds(long conversationId) {
        String sql = "SELECT user_id FROM conversation_members WHERE conversation_id = ? ORDER BY user_id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, conversationId);
            try (ResultSet result = statement.executeQuery()) {
                java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
                while (result.next()) {
                    ids.add(result.getLong(1));
                }
                return ids.stream().mapToLong(Long::longValue).toArray();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load conversation members", exception);
        }
    }

    private static OptionalLong findDirect(Connection connection, long firstUserId, long secondUserId) throws SQLException {
        String sql = """
                SELECT c.id
                FROM conversations c
                JOIN conversation_members cm ON cm.conversation_id = c.id
                WHERE c.type = 'DIRECT'
                GROUP BY c.id
                HAVING COUNT(*) = 2
                   AND COUNT(*) FILTER (WHERE cm.user_id IN (?, ?)) = 2
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, firstUserId);
            statement.setLong(2, secondUserId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? OptionalLong.of(result.getLong(1)) : OptionalLong.empty();
            }
        }
    }

    private static long createDirect(Connection connection, long firstUserId, long secondUserId, long createdAt) throws SQLException {
        long conversationId;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO conversations (type, created_at) VALUES ('DIRECT', ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, createdAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Conversation insert returned no generated id");
                }
                conversationId = keys.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO conversation_members (conversation_id, user_id, role, joined_at) VALUES (?, ?, 'MEMBER', ?)")) {
            statement.setLong(1, conversationId);
            statement.setLong(2, firstUserId);
            statement.setLong(3, createdAt);
            statement.addBatch();
            statement.setLong(1, conversationId);
            statement.setLong(2, secondUserId);
            statement.setLong(3, createdAt);
            statement.addBatch();
            statement.executeBatch();
        }
        return conversationId;
    }
}
