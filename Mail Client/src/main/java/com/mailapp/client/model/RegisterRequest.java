package com.mailapp.client.model;

public record RegisterRequest(
        String username,
        String email,
        String password,
        String fullName,
        String role
) {}

