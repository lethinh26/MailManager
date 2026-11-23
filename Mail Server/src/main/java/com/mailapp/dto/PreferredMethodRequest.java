package com.mailapp.dto;

import com.mailapp.model.AuthType;
import jakarta.validation.constraints.NotNull;

/**
 * Request đổi phương thức đăng nhập chính (preferredMethod) cho một hộp thư Gmail đã liên kết.
 */
public record PreferredMethodRequest(
        @NotNull(message = "Phương thức đăng nhập chính không được để trống (OAUTH2 hoặc APP_PASSWORD).")
        AuthType preferredMethod
) {}

