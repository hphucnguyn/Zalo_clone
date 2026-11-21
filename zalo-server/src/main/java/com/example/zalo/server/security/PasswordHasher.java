package com.example.zalo.server.security;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.util.Arrays;
import java.util.Objects;

/** BCrypt password hashing. Plain-text character arrays are cleared as soon as possible. */
public final class PasswordHasher {

    private final int rounds;

    public PasswordHasher(int rounds) {
        if (rounds < 4 || rounds > 31) {
            throw new IllegalArgumentException("BCrypt rounds must be between 4 and 31");
        }
        this.rounds = rounds;
    }

    public String hash(String password) {
        Objects.requireNonNull(password, "password must not be null");
        char[] characters = password.toCharArray();
        try {
            return BCrypt.withDefaults().hashToString(rounds, characters);
        } finally {
            Arrays.fill(characters, '\0');
        }
    }

    public boolean matches(String password, String bcryptHash) {
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(bcryptHash, "bcryptHash must not be null");
        char[] characters = password.toCharArray();
        try {
            return BCrypt.verifyer().verify(characters, bcryptHash).verified;
        } finally {
            Arrays.fill(characters, '\0');
        }
    }
}
