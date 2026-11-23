package com.mailapp.dto;

import com.mailapp.model.Role;
import com.mailapp.model.User;

/**
 * DTO thông tin người dùng trả về sau khi đăng nhập, đăng ký hoặc gọi EP-A3 /api/auth/me.
 * Tuyệt đối không bao giờ chứa thông tin nhạy cảm như passwordHash.
 */
public record UserDto(
    Long id,
    String username,
    String fullName,
    String email,
    Role role,
    boolean mustChangePassword
) {
    public static UserDto from(User user) {
        if (user == null) {
            return null;
        }
        return new UserDto(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            user.getRole(),
            user.isMustChangePassword()
        );
    }
}
