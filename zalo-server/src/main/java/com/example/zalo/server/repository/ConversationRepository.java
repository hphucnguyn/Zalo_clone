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

    /** Returns users sharing at least one conversation with {@code userId}, excluding the user themselves. */
    public long[] relatedUserIds(long userId) {
        String sql = """
                SELECT DISTINCT other_members.user_id
                FROM conversation_members mine
                JOIN conversation_members other_members ON other_members.conversation_id = mine.conversation_id
                WHERE mine.user_id = ? AND other_members.user_id <> ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
                while (result.next()) {
                    ids.add(result.getLong(1));
                }
                return ids.stream().mapToLong(Long::longValue).toArray();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load related users", exception);
        }
    }

    public long createGroup(long ownerId, String name, long createdAt) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO conversations (type, name, owner_id, created_at) VALUES ('GROUP', ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, name);
                insert.setLong(2, ownerId);
                insert.setLong(3, createdAt);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Group insert returned no id");
                    long id = keys.getLong(1);
                    addMember(connection, id, ownerId, "OWNER", createdAt);
                    connection.commit();
                    return id;
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create group", exception);
        }
    }

    public boolean isOwner(long conversationId, long userId) {
        String sql = "SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ? AND role = 'OWNER'";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, conversationId); statement.setLong(2, userId);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException exception) { throw new IllegalStateException("Could not check group owner", exception); }
    }

    public void addGroupMember(long conversationId, long userId, long joinedAt) {
        try (Connection connection = dataSource.getConnection()) { addMember(connection, conversationId, userId, "MEMBER", joinedAt); }
        catch (SQLException exception) { if ("23505".equals(exception.getSQLState())) return; throw new IllegalStateException("Could not add group member", exception); }
    }

    public void removeGroupMember(long conversationId, long userId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM conversation_members WHERE conversation_id = ? AND user_id = ?")) {
            statement.setLong(1, conversationId); statement.setLong(2, userId); statement.executeUpdate();
        } catch (SQLException exception) { throw new IllegalStateException("Could not remove group member", exception); }
    }

    public boolean isGroup(long conversationId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM conversations WHERE id = ? AND type = 'GROUP'")) {
            statement.setLong(1, conversationId);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException exception) { throw new IllegalStateException("Could not check group", exception); }
    }

    private static void addMember(Connection connection, long conversationId, long userId, String role, long joinedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO conversation_members (conversation_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, conversationId); statement.setLong(2, userId); statement.setString(3, role); statement.setLong(4, joinedAt);
            statement.executeUpdate();
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
