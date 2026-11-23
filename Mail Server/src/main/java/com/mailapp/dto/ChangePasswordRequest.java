package com.mailapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO yêu cầu đổi mật khẩu (EP-A4: POST /api/auth/change-password).
 */
public record ChangePasswordRequest(
    @NotBlank(message = "Mật khẩu hiện tại không được để trống")
    String currentPassword,

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 8, max = 128, message = "Mật khẩu mới phải có từ 8 đến 128 ký tự")
    String newPassword
) {}

