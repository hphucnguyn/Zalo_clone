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

    public Optional<UserAccount> findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, display_name, avatar_path FROM users WHERE username = ?";
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

    public UserAccount create(String username, String passwordHash, String displayName, long createdAt) {
        String sql = "INSERT INTO users (username, password_hash, display_name, created_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, displayName);
            statement.setLong(4, createdAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("User insert returned no generated id");
                }
                return new UserAccount(keys.getLong(1), username, passwordHash, displayName, null);
            }
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
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
                result.getString("avatar_path"));
    }

    public record UserAccount(long id, String username, String passwordHash, String displayName, String avatarPath) {
    }

    public static final class UsernameAlreadyExistsException extends RuntimeException {
        public UsernameAlreadyExistsException() {
            super("Username already exists");
        }
    }
}
