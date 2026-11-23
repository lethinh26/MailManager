package com.mailapp.oauth2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO nhận thông tin tài khoản từ Google UserInfo API.
 */
public record GoogleUserInfo(
    String id,
    String email,
    @JsonProperty("verified_email") Boolean verifiedEmail,
    @JsonProperty("email_verified") Boolean emailVerified,
    String name,
    String picture
) {
    public boolean isEmailVerified() {
        return Boolean.TRUE.equals(verifiedEmail) || Boolean.TRUE.equals(emailVerified);
    }
}
