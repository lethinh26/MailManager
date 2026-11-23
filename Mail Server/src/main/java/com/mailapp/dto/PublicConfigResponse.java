package com.mailapp.dto;

/**
 * DTO cấu hình công khai và giới hạn hệ thống an toàn (EP-P1: GET /api/public/config).
 * Tuyệt đối không phơi bày bất kỳ secret hay cờ nội bộ nhạy cảm nào.
 */
public record PublicConfigResponse(
    boolean registrationEnabled,
    boolean oauth2LinkEnabled,
    boolean appPasswordLinkEnabled,
    boolean maintenanceMode,
    boolean sendMailEnabled,
    int maxGmailPerUser,
    int maxAttachmentSizeMb
) {}

