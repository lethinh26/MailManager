package com.mailapp.client.net;

public class ApiException extends RuntimeException {

    private final int statusCode;
    private final String code;
    private final int retryAfterSeconds;

    public ApiException(int statusCode, String code, String message, int retryAfterSeconds) {
        super(message);
        this.statusCode = statusCode;
        this.code = code != null ? code : "";
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ApiException(int statusCode, String code, String message) {
        this(statusCode, code, message, 0);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCode() {
        return code;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public String toString() {
        return "ApiException{" +
                "statusCode=" + statusCode +
                ", code='" + code + '\'' +
                ", message='" + getMessage() + '\'' +
                ", retryAfterSeconds=" + retryAfterSeconds +
                '}';
    }
}

