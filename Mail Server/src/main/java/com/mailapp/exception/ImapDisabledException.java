package com.mailapp.exception;

import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ khi người dùng chưa bật tính năng IMAP trong cài đặt của Gmail.
 * Ánh xạ HTTP 422 Unprocessable Entity - mã IMAP_DISABLED.
 *
 * @author Lê Thanh Cường (Module M07)
 */
public class ImapDisabledException extends MailConnectionException {

    public ImapDisabledException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "IMAP_DISABLED", message);
    }

    public ImapDisabledException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "IMAP_DISABLED", message, cause);
    }

    public ImapDisabledException() {
        this("Tài khoản Gmail chưa bật tính năng IMAP trong phần Cài đặt của Google Mail.");
    }
}

