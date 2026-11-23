package com.mailapp.dto;

/**
 * DTO phản hồi kết quả đăng nhập thành công kèm JWT Access Token (EP-A2).
 */
public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    UserDto user
) {
    public static LoginResponse of(String accessToken, long expiresIn, UserDto user) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, user);
    }
}

