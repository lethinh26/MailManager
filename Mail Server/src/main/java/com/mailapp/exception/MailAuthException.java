package com.mailapp.exception;

import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ xác thực Mail thất bại (sai App Password, access token không hợp lệ).
 * Ánh xạ HTTP 422 Unprocessable Entity - mã GMAIL_AUTH_FAILED.
 *
 * @author Lê Thanh Cường (Module M07)
 */
public class MailAuthException extends MailConnectionException {

    public MailAuthException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "GMAIL_AUTH_FAILED", message);
    }

    public MailAuthException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "GMAIL_AUTH_FAILED", message, cause);
    }

    public MailAuthException() {
        this("Xác thực Gmail thất bại. Vui lòng kiểm tra lại App Password hoặc quyền truy cập.");
    }
}

