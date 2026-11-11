package com.kofi.pos.model; // Domain-model package.

/** Local login credentials; the password is stored as salt and hash. */
public record User(
        String username,      // Login name (compared case-insensitively).
        String salt,          // Random Base64 salt mixed into the password hash.
        String passwordHash) { } // PBKDF2 hash as hex; the real password is never stored.
