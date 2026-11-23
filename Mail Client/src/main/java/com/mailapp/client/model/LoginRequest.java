package com.mailapp.client.model;

public record LoginRequest(
        String username,
        String password
) {}

