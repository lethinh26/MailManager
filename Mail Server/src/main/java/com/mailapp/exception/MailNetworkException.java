package com.mailapp.exception;

import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ lỗi mạng hoặc timeout khi kết nối tới Gmail (DNS, timeout, TLS handshake, v.v.).
 * Ánh xạ HTTP 502 Bad Gateway / 504 Gateway Timeout - mã GMAIL_CONNECTION_ERROR.
 *
 * @author Lê Thanh Cường (Module M07)
 */
public class MailNetworkException extends MailConnectionException {

    public MailNetworkException(String message, boolean isTimeout) {
        super(isTimeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY, "GMAIL_CONNECTION_ERROR", message);
    }

    public MailNetworkException(String message, Throwable cause, boolean isTimeout) {
        super(isTimeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY, "GMAIL_CONNECTION_ERROR", message, cause);
    }

    public MailNetworkException(String message) {
        this(message, false);
    }

    public MailNetworkException(String message, Throwable cause) {
        this(message, cause, false);
    }
}

