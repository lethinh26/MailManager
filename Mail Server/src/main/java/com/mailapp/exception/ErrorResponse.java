package com.mailapp.exception;

import java.time.LocalDateTime;

/**
 * Cấu trúc phản hồi lỗi chuẩn thống nhất của hệ thống (chứa cả "code" và "errorCode"):
 * { "timestamp": "...", "status": 403, "error": "Forbidden", "message": "...", "code": "...", "errorCode": "..." }
 */
public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String code,
    String errorCode,
    String path
) {
    public static ErrorResponse of(int status, String error, String message, String code) {
        return of(status, error, message, code, null);
    }

    public static ErrorResponse of(int status, String error, String message, String code, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, code, code, path);
    }
}
