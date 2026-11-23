package com.mailapp.exception;

import org.springframework.http.HttpStatus;

/**
 * Lớp ngoại lệ cơ sở (unchecked) cho tất cả các lỗi liên quan đến kết nối Mail (IMAP / SMTP).
 * Đảm bảo message không bao giờ chứa thông tin bí mật hay credentials.
 *
 * @author Lê Thanh Cường (Module M07)
 */
public class MailConnectionException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public MailConnectionException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public MailConnectionException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

