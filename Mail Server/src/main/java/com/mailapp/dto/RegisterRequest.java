package com.mailapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO yêu cầu đăng ký tài khoản mới (EP-A1: POST /api/auth/register).
 * Role client gửi lên (nếu có) luôn bị hệ thống bỏ qua, mặc định gán USER.
 */
public record RegisterRequest(
    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 3, max = 64, message = "Tên đăng nhập phải từ 3 đến 64 ký tự")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Tên đăng nhập chỉ chứa chữ cái, chữ số và dấu gạch dưới")
    String username,

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    String email,

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, max = 128, message = "Mật khẩu phải từ 8 đến 128 ký tự")
    String password,

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 255, message = "Họ và tên tối đa 255 ký tự")
    String fullName,

    String role // Client gửi giá trị gì thì server cũng bỏ qua, luôn gán ROLE_USER
) {}

