package com.mailapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserDto user
) {}

