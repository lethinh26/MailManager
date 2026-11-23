package com.mailapp.dto;

import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;

import java.time.LocalDateTime;

/**
 * DTO trả về thông tin tài khoản Gmail đã liên kết (EP-G1, EP-G2, PATCH, PUT preferred-method, DELETE method).
 * Tuyệt đối không chứa App Password, Refresh Token hay Access Token.
 */
public record GmailAccountResponse(
        Long id,
        String email,
        String emailKey,
        String displayName,
        AuthType authType,
        AuthType preferredMethod,
        AuthType activeMethod,
        boolean fallbackActive,
        MethodsInfo methods,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime tokenExpiresAt,
        LocalDateTime lastVerifiedAt,
        LinkOutcome outcome,
        String message
) {
    /**
     * Thông tin các phương thức đăng nhập đang có trên cùng một hộp thư Gmail.
     */
    public record MethodsInfo(boolean hasOAuth, boolean hasAppPassword) {}

    /**
     * Kết quả nghiệp vụ khi liên kết hoặc bổ sung phương thức đăng nhập cho một hộp thư Gmail.
     */
    public enum LinkOutcome {
        CREATED,
        METHOD_ADDED,
        METHOD_UPDATED
    }

    public static GmailAccountResponse from(GmailAccount account) {
        return from(account, null, null);
    }

    public static GmailAccountResponse from(GmailAccount account, LinkOutcome outcome, String message) {
        AuthType preferred = account.getPreferredMethod();
        AuthType active = account.getActiveMethod();
        return new GmailAccountResponse(
                account.getId(),
                account.getEmail(),
                account.getEmailKey(),
                account.getDisplayName(),
                preferred,
                preferred,
                active,
                account.isFallbackActive(),
                new MethodsInfo(account.hasOAuth(), account.hasAppPassword()),
                account.getStatus(),
                account.getCreatedAt(),
                account.getTokenExpiresAt(),
                account.getLastVerifiedAt(),
                outcome,
                message
        );
    }
}
