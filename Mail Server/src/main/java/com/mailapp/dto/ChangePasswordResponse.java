package com.mailapp.dto;

/**
 * DTO phản hồi đổi mật khẩu thành công (EP-A4).
 * Cấp Access Token JWT mới cập nhật claims (mustChangePassword=false).
 */
public record ChangePasswordResponse(
    String message,
    String accessToken,
    String tokenType,
    long expiresIn,
    UserDto user
) {}

