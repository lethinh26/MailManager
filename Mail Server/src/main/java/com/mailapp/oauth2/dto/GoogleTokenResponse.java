package com.mailapp.oauth2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO nhận phản hồi token từ Google OAuth2 token endpoint.
 */
public record GoogleTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("expires_in") Long expiresIn,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("scope") String scope,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("id_token") String idToken
) {}
