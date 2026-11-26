package com.mailapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO tài khoản Gmail liên kết phía Client (hỗ trợ đa phương thức OAuth2 + App Password và trạng thái fallback).
 * Tuyệt đối không chứa mật khẩu ứng dụng hay token.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GmailAccountDto(
        Long id,
        String email,
        String emailKey,
        String displayName,
        String authType,
        String preferredMethod,
        String activeMethod,
        Boolean fallbackActive,
        MethodsInfo methods,
        String status,
        String tokenExpiresAt,
        String createdAt,
        String updatedAt,
        String lastVerifiedAt,
        String outcome,
        String message
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MethodsInfo(boolean hasOAuth, boolean hasAppPassword) {}

    // Constructor tương thích ngược với các chỗ gọi cũ 8 tham số
    public GmailAccountDto(Long id, String email, String displayName, String authType, String status,
                           String tokenExpiresAt, String createdAt, String updatedAt) {
        this(id, email, null, displayName, authType, authType, authType, false,
                defaultMethods(authType), status, tokenExpiresAt, createdAt, updatedAt, null, null, null);
    }

    // Constructor tương thích ngược với các chỗ gọi cũ 9 tham số
    public GmailAccountDto(Long id, String email, String displayName, String authType, String status,
                           String tokenExpiresAt, String createdAt, String updatedAt, String lastVerifiedAt) {
        this(id, email, null, displayName, authType, authType, authType, false,
                defaultMethods(authType), status, tokenExpiresAt, createdAt, updatedAt, lastVerifiedAt, null, null);
    }

    private static MethodsInfo defaultMethods(String authType) {
        boolean oauth = "OAUTH2".equalsIgnoreCase(authType);
        boolean appPass = "APP_PASSWORD".equalsIgnoreCase(authType);
        return new MethodsInfo(oauth, appPass);
    }

    public boolean hasOAuth() {
        if (methods != null) return methods.hasOAuth();
        return "OAUTH2".equalsIgnoreCase(authType);
    }

    public boolean hasAppPassword() {
        if (methods != null) return methods.hasAppPassword();
        return "APP_PASSWORD".equalsIgnoreCase(authType);
    }

    public String effectivePreferredMethod() {
        if (preferredMethod != null && !preferredMethod.isBlank()) return preferredMethod;
        return authType != null ? authType : (hasOAuth() ? "OAUTH2" : "APP_PASSWORD");
    }

    public String effectiveActiveMethod() {
        if (activeMethod != null && !activeMethod.isBlank()) return activeMethod;
        if (isFallbackActive() && hasAppPassword()) return "APP_PASSWORD";
        return effectivePreferredMethod();
    }

    public boolean isFallbackActive() {
        return Boolean.TRUE.equals(fallbackActive);
    }

    /** Mirror the server's NEEDS_REAUTH gate, not merely the last fallback indicator. */
    public boolean requiresReauthBeforeMail() {
        if (!"NEEDS_REAUTH".equalsIgnoreCase(status)) return false;
        return !(hasAppPassword() && ("OAUTH2".equalsIgnoreCase(effectivePreferredMethod())
                || ("APP_PASSWORD".equalsIgnoreCase(effectivePreferredMethod()) && hasOAuth())));
    }
}
