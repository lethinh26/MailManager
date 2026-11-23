package com.mailapp.oauth2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Tiện ích sinh mã PKCE (Proof Key for Code Exchange - RFC 7636).
 * Tạo code_verifier và code_challenge chuẩn S256 bảo vệ luồng Google OAuth2.
 *
 * @author Lê Thanh Cường (Module M06)
 */
public final class PkceUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PkceUtil() {}

    /**
     * Sinh code_verifier ngẫu nhiên có độ entropy cao (32 bytes = 256 bits),
     * mã hóa Base64 URL-safe không padding (43 ký tự).
     */
    public static String generateCodeVerifier() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Sinh code_challenge từ code_verifier bằng thuật toán SHA-256 (S256).
     */
    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuật toán SHA-256 không khả dụng trên môi trường JVM", e);
        }
    }
}

