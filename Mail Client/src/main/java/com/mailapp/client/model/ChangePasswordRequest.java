package com.mailapp.client.model;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
) {}

