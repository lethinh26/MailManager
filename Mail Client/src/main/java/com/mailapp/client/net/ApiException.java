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

    /** A failed DELETE response may arrive after the IMAP mutation committed. */
    public static boolean isDeleteOutcomeUncertain(Throwable error) {
        return isMutationOutcomeUncertain(error);
    }

    /** An external mail mutation may have committed before a failed reply. */
    public static boolean isMutationOutcomeUncertain(Throwable error) {
        while (error instanceof java.util.concurrent.ExecutionException && error.getCause() != null) {
            error = error.getCause();
        }
        if (error instanceof ApiException api) {
            return api.statusCode >= 500 && !"MAINTENANCE_MODE".equals(api.code)
                    && !"AUDIT_UNAVAILABLE".equals(api.code);
        }
        return error instanceof java.io.IOException || error instanceof InterruptedException
                || error instanceof java.util.concurrent.CancellationException;
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

