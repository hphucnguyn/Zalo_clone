package com.example.zalo.server.security;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validation and normalization for authentication input. */
public final class InputValidator {

    private static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9_]{3,50}");

    private InputValidator() {
    }

    public static String username(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Username must contain 3-50 letters, digits, or underscores");
        }
        return normalized;
    }

    public static String registrationPassword(String value) {
        if (value == null || value.length() < 8 || value.length() > 128) {
            throw new IllegalArgumentException("Password must contain 8-128 characters");
        }
        return value;
    }

    public static String loginPassword(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("Invalid password");
        }
        return value;
    }

    public static String displayName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new IllegalArgumentException("Display name must contain 1-100 characters");
        }
        return normalized;
    }
}
