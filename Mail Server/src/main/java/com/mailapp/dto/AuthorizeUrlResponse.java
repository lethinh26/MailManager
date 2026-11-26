package com.mailapp.dto;

/**
 * Phản hồi chứa URL ủy quyền OAuth2 của Google.
 */
public record AuthorizeUrlResponse(
    String authorizeUrl,
    String pollToken
) {
    public AuthorizeUrlResponse(String authorizeUrl) {
        this(authorizeUrl, null);
    }
}

