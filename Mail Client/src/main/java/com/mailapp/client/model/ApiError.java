package com.mailapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiError(
        int status,
        String error,
        String message,
        String code,
        String errorCode,
        int retryAfterSeconds
) {
    public String getEffectiveCode() {
        if (code != null && !code.isBlank()) return code;
        if (errorCode != null && !errorCode.isBlank()) return errorCode;
        return null;
    }
}

