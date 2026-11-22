package com.example.zalo.server.repository;

import com.example.zalo.common.model.UserDto;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Stores pending requests and symmetric friendship edges. */
public final class FriendRepository {
    private final DataSource dataSource;
    private final UserRepository users;

    public FriendRepository(DataSource dataSource, UserRepository users) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.users = Objects.requireNonNull(users);
    }

    public boolean areFriends(long first, long second) {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(
                "SELECT 1 FROM friendships WHERE user_id=? AND friend_id=?")) {
            p.setLong(1, first); p.setLong(2, second);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        } catch (SQLException e) { throw new IllegalStateException("Could not check friendship", e); }
    }

    public OptionalLong findPending(long sender, long receiver) {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(
                "SELECT id FROM friend_requests WHERE sender_id=? AND receiver_id=? AND status='PENDING'")) {
            p.setLong(1, sender); p.setLong(2, receiver);
            try (ResultSet r = p.executeQuery()) { return r.next() ? OptionalLong.of(r.getLong(1)) : OptionalLong.empty(); }
        } catch (SQLException e) { throw new IllegalStateException("Could not load friend request", e); }
    }

    public long createRequest(long sender, long receiver, long now) {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO friend_requests(sender_id,receiver_id,created_at) VALUES(?,?,?) RETURNING id")) {
            p.setLong(1, sender); p.setLong(2, receiver); p.setLong(3, now);
            try (ResultSet r = p.executeQuery()) { r.next(); return r.getLong(1); }
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new RequestAlreadyPendingException();
            throw new IllegalStateException("Could not create friend request", e);
        }
    }

    public List<Map<String,Object>> incoming(long receiver) {
        String sql = "SELECT fr.id, fr.created_at, u.id user_id, u.username, u.display_name, u.avatar_path FROM friend_requests fr JOIN users u ON u.id=fr.sender_id WHERE fr.receiver_id=? AND fr.status='PENDING' ORDER BY fr.created_at DESC";
        try (Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setLong(1, receiver);
            try (ResultSet r=p.executeQuery()) {
                List<Map<String,Object>> result=new ArrayList<>();
                while(r.next()) {
                    UserDto user=new UserDto(r.getLong("user_id"),r.getString("username"),r.getString("display_name"),r.getString("avatar_path"),false);
                    result.add(Map.of("requestId",r.getLong("id"),"createdAt",r.getLong("created_at"),"user",user));
                }
                return List.copyOf(result);
            }
        } catch(SQLException e) { throw new IllegalStateException("Could not list friend requests",e); }
    }

    public OptionalLong respond(long requestId, long receiver, boolean accept, long now) {
        try (Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                long sender;
                try (PreparedStatement p=c.prepareStatement("SELECT sender_id FROM friend_requests WHERE id=? AND receiver_id=? AND status='PENDING' FOR UPDATE")) {
                    p.setLong(1,requestId); p.setLong(2,receiver);
                    try(ResultSet r=p.executeQuery()) { if(!r.next()) { c.rollback(); return OptionalLong.empty(); } sender=r.getLong(1); }
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE friend_requests SET status=?, responded_at=? WHERE id=?")) {
                    p.setString(1,accept?"ACCEPTED":"REJECTED"); p.setLong(2,now); p.setLong(3,requestId); p.executeUpdate();
                }
                if(accept) try(PreparedStatement p=c.prepareStatement("INSERT INTO friendships(user_id,friend_id,created_at) VALUES(?,?,?) ON CONFLICT DO NOTHING")) {
                    p.setLong(1,receiver); p.setLong(2,sender); p.setLong(3,now); p.addBatch();
                    p.setLong(1,sender); p.setLong(2,receiver); p.setLong(3,now); p.addBatch(); p.executeBatch();
                }
                c.commit(); return OptionalLong.of(sender);
            } catch(SQLException|RuntimeException e) { c.rollback(); throw e; }
        } catch(SQLException e) { throw new IllegalStateException("Could not respond to friend request",e); }
    }

    public static final class RequestAlreadyPendingException extends RuntimeException { }
}
