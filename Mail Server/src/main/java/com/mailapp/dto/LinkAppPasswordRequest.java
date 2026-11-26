package com.mailapp.dto;

import com.mailapp.model.LinkIntent;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO cho endpoint EP-G2: Liên kết tài khoản Gmail bằng App Password 16 ký tự.
 * toString() luôn che mật khẩu để đảm bảo không bao giờ rò rỉ vào log.
 */
public record LinkAppPasswordRequest(
        @NotBlank(message = "Địa chỉ email không được để trống.")
        @Email(message = "Địa chỉ email không đúng định dạng.")
        @Size(max = 255, message = "Địa chỉ email tối đa 255 ký tự.")
        String email,

        @NotBlank(message = "App Password không được để trống.")
        @Size(min = 8, max = 64, message = "App Password không hợp lệ.")
        String appPassword,

        @Size(max = 255, message = "Tên hiển thị tối đa 255 ký tự.")
        String displayName,
        LinkIntent intent
) {
    public LinkAppPasswordRequest(String email, String appPassword, String displayName) {
        this(email, appPassword, displayName, LinkIntent.CREATE);
    }

    public LinkAppPasswordRequest {
        if (intent == null) intent = LinkIntent.CREATE;
    }

    @Override
    public String toString() {
        return "LinkAppPasswordRequest[email=" + email + ", appPassword=***, displayName=" + displayName + "]";
    }
}

