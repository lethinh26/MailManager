package com.mailapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDto(
        Long id,
        String username,
        String fullName,
        String email,
        String role,
        boolean mustChangePassword,
        String createdAt
) {}

