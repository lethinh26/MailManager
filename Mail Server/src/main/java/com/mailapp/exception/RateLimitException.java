package com.mailapp.exception;

/**
 * Ngoại lệ khi người dùng vượt quá số lần đăng nhập sai cho phép (HTTP 429 Too Many Requests).
 * Kèm thông tin thời gian cần chờ trước khi thử lại (Retry-After tính bằng giây).
 */
public class RateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

