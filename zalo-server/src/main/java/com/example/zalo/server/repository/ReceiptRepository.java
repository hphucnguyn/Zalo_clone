package com.example.zalo.server.repository;

import com.example.zalo.common.model.MessageStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/** Persists per-recipient delivery and read receipts. Updates only move a receipt forward. */
public class ReceiptRepository {

    private final DataSource dataSource;

    public ReceiptRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public void createSentReceipts(long messageId, long senderId, long[] memberIds) {
        String sql = "INSERT INTO message_receipts (message_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (long userId : memberIds) {
                if (userId == senderId) {
                    continue;
                }
                statement.setLong(1, messageId);
                statement.setLong(2, userId);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create message receipts", exception);
        }
    }

    public boolean markDelivered(long messageId, long userId, long timestamp) {
        return update(messageId, userId, timestamp, false);
    }

    public boolean markSeen(long messageId, long userId, long timestamp) {
        return update(messageId, userId, timestamp, true);
    }

    private boolean update(long messageId, long userId, long timestamp, boolean seen) {
        String sql = seen
                ? "UPDATE message_receipts SET delivered_at = COALESCE(delivered_at, ?), seen_at = COALESCE(seen_at, ?) WHERE message_id = ? AND user_id = ?"
                : "UPDATE message_receipts SET delivered_at = COALESCE(delivered_at, ?) WHERE message_id = ? AND user_id = ? AND seen_at IS NULL";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, timestamp);
            int index = 2;
            if (seen) {
                statement.setLong(index++, timestamp);
            }
            statement.setLong(index++, messageId);
            statement.setLong(index, userId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update message receipt", exception);
        }
    }
}
