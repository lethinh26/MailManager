package com.mailapp.exception;

import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ khi refresh token bị Google thu hồi hoặc hết hạn (invalid_grant).
 * Yêu cầu người dùng thực hiện liên kết lại tài khoản Gmail.
 * Ánh xạ HTTP 409 Conflict - mã GMAIL_NEEDS_REAUTH.
 *
 * @author Lê Thanh Cường (Module M07)
 */
public class ReauthRequiredException extends MailConnectionException {

    public ReauthRequiredException(String message) {
        super(HttpStatus.CONFLICT, "GMAIL_NEEDS_REAUTH", message);
    }

    public ReauthRequiredException(String message, Throwable cause) {
        super(HttpStatus.CONFLICT, "GMAIL_NEEDS_REAUTH", message, cause);
    }

    public ReauthRequiredException() {
        this("Phiên ủy quyền Google đã hết hạn hoặc bị thu hồi. Vui lòng liên kết lại tài khoản Gmail.");
    }
}

