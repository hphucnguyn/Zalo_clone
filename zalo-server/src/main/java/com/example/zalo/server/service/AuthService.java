package com.example.zalo.server.service;

import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.util.TimeUtil;
import com.example.zalo.server.repository.UserRepository;
import com.example.zalo.server.security.InputValidator;
import com.example.zalo.server.security.LoginAttemptLimiter;
import com.example.zalo.server.security.PasswordHasher;

import java.util.Objects;
import java.util.Optional;

/** Authentication use cases. This layer never logs or returns a plain-text password. */
public final class AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            LoginAttemptLimiter loginAttemptLimiter) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        this.loginAttemptLimiter = Objects.requireNonNull(loginAttemptLimiter, "loginAttemptLimiter must not be null");
    }

    public AuthResult register(String usernameInput, String passwordInput, String displayNameInput, String phoneInput) {
        final String username;
        final String password;
        final String displayName;
        final String phoneNumber;
        try {
            username = InputValidator.username(usernameInput);
            password = InputValidator.registrationPassword(passwordInput);
            displayName = InputValidator.displayName(displayNameInput);
            phoneNumber = phoneInput == null || phoneInput.isBlank() ? null : InputValidator.phoneNumber(phoneInput);
        } catch (IllegalArgumentException exception) {
            return AuthResult.failure(AuthResult.Failure.INVALID_REGISTRATION);
        }

        if (userRepository.findByUsername(username).isPresent()) {
            return AuthResult.failure(AuthResult.Failure.USERNAME_UNAVAILABLE);
        }
        try {
            UserRepository.UserAccount account = userRepository.create(
                    username, passwordHasher.hash(password), displayName, phoneNumber, TimeUtil.nowEpochMillis());
            return AuthResult.success(toUserDto(account));
        } catch (UserRepository.UsernameAlreadyExistsException exception) {
            return AuthResult.failure(AuthResult.Failure.USERNAME_UNAVAILABLE);
        } catch (UserRepository.PhoneAlreadyExistsException exception) {
            return AuthResult.failure(AuthResult.Failure.PHONE_UNAVAILABLE);
        }
    }

    public AuthResult login(String usernameInput, String passwordInput) {
        final String username;
        final String password;
        try {
            username = InputValidator.username(usernameInput);
            password = InputValidator.loginPassword(passwordInput);
        } catch (IllegalArgumentException exception) {
            return AuthResult.failure(AuthResult.Failure.INVALID_CREDENTIALS);
        }

        if (loginAttemptLimiter.isLocked(username)) {
            return AuthResult.failure(AuthResult.Failure.TEMPORARILY_LOCKED);
        }
        Optional<UserRepository.UserAccount> account = userRepository.findByUsername(username);
        if (account.isEmpty() || !passwordHasher.matches(password, account.get().passwordHash())) {
            boolean locked = loginAttemptLimiter.recordFailure(username);
            return AuthResult.failure(locked
                    ? AuthResult.Failure.TEMPORARILY_LOCKED
                    : AuthResult.Failure.INVALID_CREDENTIALS);
        }

        loginAttemptLimiter.clear(username);
        return AuthResult.success(toUserDto(account.get()));
    }

    private static UserDto toUserDto(UserRepository.UserAccount account) {
        return new UserDto(account.id(), account.username(), account.displayName(), account.avatarPath(), false);
    }
}
