package com.example.zalo.server.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import com.example.zalo.common.model.UserDto;

/** JDBC access for the users table. Passwords are only handled as BCrypt hashes. */
public class UserRepository {

    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public DataSource dataSource() { return dataSource; }

    public Optional<UserAccount> findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, display_name, avatar_path, phone_number FROM users WHERE username = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readAccount(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load user", exception);
        }
    }

    public Optional<UserDto> findPublicByUsername(String username) {
        return findByUsername(username).map(account -> new UserDto(
                account.id(), account.username(), account.displayName(), account.avatarPath(), false));
    }

    public Optional<UserDto> findPublicByPhone(String phoneNumber) {
        String sql = "SELECT id, username, display_name, avatar_path FROM users WHERE phone_number = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, phoneNumber);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new UserDto(result.getLong("id"), result.getString("username"),
                        result.getString("display_name"), result.getString("avatar_path"), false)) : Optional.empty();
            }
        } catch (SQLException exception) { throw new IllegalStateException("Could not search user by phone", exception); }
    }

    public java.util.List<UserDto> findFriends(long userId) {
        String sql = "SELECT u.id, u.username, u.display_name, u.avatar_path FROM friendships f JOIN users u ON u.id = f.friend_id WHERE f.user_id = ? ORDER BY u.display_name";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                java.util.ArrayList<UserDto> users = new java.util.ArrayList<>();
                while (result.next()) users.add(new UserDto(result.getLong("id"), result.getString("username"), result.getString("display_name"), result.getString("avatar_path"), false));
                return java.util.List.copyOf(users);
            }
        } catch (SQLException exception) { throw new IllegalStateException("Could not load friends", exception); }
    }

    public Optional<UserDto> findPublicById(long userId) {
        String sql = "SELECT id, username, display_name, avatar_path FROM users WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new UserDto(result.getLong("id"), result.getString("username"),
                        result.getString("display_name"), result.getString("avatar_path"), false));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load public user", exception);
        }
    }

    public UserAccount create(String username, String passwordHash, String displayName, String phoneNumber, long createdAt) {
        String sql = "INSERT INTO users (username, password_hash, display_name, phone_number, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, displayName);
            statement.setString(4, phoneNumber);
            statement.setLong(5, createdAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("User insert returned no generated id");
                }
                return new UserAccount(keys.getLong(1), username, passwordHash, displayName, null, phoneNumber);
            }
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                if (exception.getMessage() != null && exception.getMessage().contains("phone_number")) throw new PhoneAlreadyExistsException();
                throw new UsernameAlreadyExistsException();
            }
            throw new IllegalStateException("Could not create user", exception);
        }
    }

    private static UserAccount readAccount(ResultSet result) throws SQLException {
        return new UserAccount(
                result.getLong("id"),
                result.getString("username"),
                result.getString("password_hash"),
                result.getString("display_name"),
                result.getString("avatar_path"),
                result.getString("phone_number"));
    }

    public record UserAccount(long id, String username, String passwordHash, String displayName, String avatarPath, String phoneNumber) {
        public UserAccount(long id, String username, String passwordHash, String displayName, String avatarPath) {
            this(id, username, passwordHash, displayName, avatarPath, null);
        }
    }

    public static final class UsernameAlreadyExistsException extends RuntimeException {
        public UsernameAlreadyExistsException() {
            super("Username already exists");
        }
    }
    public static final class PhoneAlreadyExistsException extends RuntimeException { }
}
