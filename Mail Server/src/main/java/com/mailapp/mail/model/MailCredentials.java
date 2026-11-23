package com.mailapp.mail.model;

import com.mailapp.mail.AccessTokenProvider;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.security.AesCipher;

import java.util.Objects;

/**
 * Record chứa thông tin xác thực để mở kết nối IMAP / SMTP.
 * Luôn che giấu thông tin bí mật (secret) trong phương thức toString().
 *
 * @author Lê Thanh Cường (Module M07)
 */
public record MailCredentials(
    String email,
    AuthType authType,
    String secret
) {
    public MailCredentials {
        Objects.requireNonNull(email, "Email không được để trống");
        Objects.requireNonNull(authType, "AuthType không được để trống");
        Objects.requireNonNull(secret, "Secret (mật khẩu hoặc token) không được để trống");
    }

    /**
     * Tạo MailCredentials từ thực thể GmailAccount theo phương thức chính (preferredMethod / authType).
     */
    public static MailCredentials from(GmailAccount account, AesCipher aesCipher, AccessTokenProvider accessTokenProvider) {
        Objects.requireNonNull(account, "GmailAccount không được null");
        Objects.requireNonNull(account.getAuthType(), "AuthType trong GmailAccount không được null");
        return forMethod(account, account.getAuthType(), aesCipher, accessTokenProvider);
    }

    /**
     * Tạo MailCredentials từ thực thể GmailAccount theo một phương thức xác thực cụ thể (dùng cho preferredMethod hoặc fallback).
     */
    public static MailCredentials forMethod(GmailAccount account,
                                            AuthType method,
                                            AesCipher aesCipher,
                                            AccessTokenProvider accessTokenProvider) {
        Objects.requireNonNull(account, "GmailAccount không được null");
        Objects.requireNonNull(account.getEmail(), "Email trong GmailAccount không được null");
        Objects.requireNonNull(method, "AuthType không được null");

        String secret;
        if (method == AuthType.APP_PASSWORD) {
            Objects.requireNonNull(aesCipher, "AesCipher không được null khi giải mã App Password");
            secret = aesCipher.decrypt(account.getAppPasswordEncrypted());
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException("Không tìm thấy App Password đã mã hóa cho tài khoản: " + maskEmail(account.getEmail()));
            }
        } else if (method == AuthType.OAUTH2) {
            Objects.requireNonNull(accessTokenProvider, "AccessTokenProvider không được null cho tài khoản OAuth2");
            secret = accessTokenProvider.getValidAccessToken(account);
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException("Không thể lấy Access Token hợp lệ cho tài khoản: " + maskEmail(account.getEmail()));
            }
        } else {
            throw new IllegalArgumentException("AuthType không được hỗ trợ: " + method);
        }

        return new MailCredentials(account.getEmail(), method, secret);
    }

    /**
     * Tạo MailCredentials cho phương thức App Password (dùng khi kiểm tra thông tin nhập từ UI).
     */
    public static MailCredentials ofAppPassword(String email, String rawAppPassword) {
        return new MailCredentials(email, AuthType.APP_PASSWORD, rawAppPassword);
    }

    /**
     * Tạo MailCredentials cho phương thức OAuth2.
     */
    public static MailCredentials ofOAuth2(String email, String accessToken) {
        return new MailCredentials(email, AuthType.OAUTH2, accessToken);
    }

    /**
     * Che email theo định dạng c***@gmail.com để log an toàn.
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    @Override
    public String toString() {
        return "MailCredentials[email=" + maskEmail(email) + ", authType=" + authType + ", secret=***]";
    }
}

