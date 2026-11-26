package com.mailapp.security;

import java.nio.charset.StandardCharsets;

/** BCrypt accepts at most 72 bytes; never silently truncate a UTF-8 password. */
public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static final int MAX_BCRYPT_BYTES = 72;

    public static boolean fitsBcrypt(String password) {
        // UTF-16 length is a cheap lower bound for UTF-8 byte length.
        return password != null && password.length() <= MAX_BCRYPT_BYTES
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_BYTES;
    }
}
