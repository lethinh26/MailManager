package com.mailapp.service;

import com.mailapp.dto.ChangePasswordRequest;
import com.mailapp.dto.ChangePasswordResponse;
import com.mailapp.dto.LoginRequest;
import com.mailapp.dto.LoginResponse;
import com.mailapp.dto.RegisterRequest;
import com.mailapp.dto.UserDto;
import com.mailapp.model.User;

/**
 * Interface nghiệp vụ xác thực tài khoản (Auth).
 * Quản lý Đăng nhập (EP-A2), Đăng ký (EP-A1) và Đổi mật khẩu (EP-A4).
 *
 * @author Lê Thanh Cường
 */
public interface AuthService {

    /**
     * [EP-A2] Xác thực thông tin đăng nhập và phát hành JWT Access Token.
     */
    LoginResponse login(LoginRequest request, String clientIp);

    /**
     * [EP-A1] Đăng ký tài khoản người dùng mới (vai trò luôn luôn là USER).
     */
    UserDto register(RegisterRequest request, String clientIp);

    /**
     * [EP-A4] Đổi mật khẩu tài khoản (cần đăng nhập), xóa cờ mustChangePassword và cấp JWT mới.
     */
    ChangePasswordResponse changePassword(ChangePasswordRequest request, User currentUser, String clientIp);
}
