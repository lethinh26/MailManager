package com.mailapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangePasswordResponse(
        String message,
        String accessToken,
        String tokenType,
        long expiresIn,
        UserDto user
) {}

