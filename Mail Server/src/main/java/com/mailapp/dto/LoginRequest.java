package com.mailapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO yêu cầu đăng nhập hệ thống (EP-A2).
 */
public record LoginRequest(
    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 3, max = 255, message = "Tên đăng nhập hoặc email phải từ 3 đến 255 ký tự")
    String username,

    @NotBlank(message = "Mật khẩu không được để trống")
    String password
) {}

