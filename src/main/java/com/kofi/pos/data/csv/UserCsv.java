package com.kofi.pos.data.csv; // CSV layer.

import com.kofi.pos.model.User; // Record stored in users.csv.

import java.util.List; // Column list.

/** Layout of users.csv. */
public final class UserCsv {
    private UserCsv() { } // Holder class.

    public static final CsvTable<User> TABLE = new CsvTable<>("users.csv",
            List.of("username", "salt", "password_hash"),
            row -> new User(row.get(0), row.get(1), row.get(2)),
            u -> List.of(u.username(), u.salt(), u.passwordHash()));
}
