package com.kofi.pos.security; // Everything about checking passwords lives here, away from business rules.

import javax.crypto.SecretKeyFactory;          // Factory for the PBKDF2 key-derivation algorithm.
import javax.crypto.spec.PBEKeySpec;           // Holds password + salt + iterations for PBKDF2.
import java.security.GeneralSecurityException; // Thrown when the algorithm is unavailable.
import java.security.MessageDigest;            // Provides a constant-time byte comparison.
import java.util.Arrays;                       // Used to wipe the password array from memory.
import java.util.Base64;                       // The salt is stored as Base64 text.
import java.util.regex.Pattern;                // Used to validate hexadecimal text.

/** Verifies a typed password against a stored salted PBKDF2 hash. */
public final class PasswordHasher {
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+"); // Matches only hex digits.
    private static final int ITERATIONS = 120_000; // Many rounds make brute-forcing slow.
    private static final int KEY_BITS = 256;       // Length of the derived hash in bits.

    private PasswordHasher() { } // Utility class.

    /** Returns true when {@code password} + {@code saltBase64} hashes to {@code hashHex}. */
    public static boolean verify(String password, String saltBase64, String hashHex) {
        try {
            byte[] expected = decodeHex(hashHex); // Stored hash as raw bytes.
            // Hash the attempted password with the stored salt.
            byte[] actual = derive(password.toCharArray(), Base64.getDecoder().decode(saltBase64));
            return MessageDigest.isEqual(expected, actual); // Compare without leaking timing information.
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false; // Malformed data or missing algorithm: treat as a failed login, never crash.
        }
    }

    /** Runs PBKDF2-HMAC-SHA256 over the password and salt. */
    private static byte[] derive(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS); // Bundle the inputs.
        Arrays.fill(password, '\0'); // Overwrite our own copy of the password straight away.
        try {
            // Ask the JDK for PBKDF2 and derive the key bytes.
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword(); // Always wipe the spec, even if derivation failed.
        }
    }

    /** Converts hex text such as "5a4f..." to bytes; throws IllegalArgumentException if it is not valid hex. */
    private static byte[] decodeHex(String text) {
        // Reject null, odd-length, or non-hex text up front.
        if (text == null || text.length() % 2 != 0 || !HEX.matcher(text).matches()) throw new IllegalArgumentException();
        byte[] bytes = new byte[text.length() / 2]; // Two hex digits make one byte.
        for (int i = 0; i < bytes.length; i++) {
            // Parse each pair of characters as a base-16 number.
            bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
