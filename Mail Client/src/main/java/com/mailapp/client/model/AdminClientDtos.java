package com.mailapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Các DTO phía Client tương ứng với docs/api-admin-contract.md (EP-AD1..AD8, EP-S1/S2, EP-ST1/ST2).
 */
public final class AdminClientDtos {

    private AdminClientDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminPageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record AdminCreateUserRequest(
            String username,
            String fullName,
            String email,
            String role,
            String password
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminUserSummary(
            Long id,
            String username,
            String fullName,
            String email,
            String role,
            boolean enabled,
            boolean mustChangePassword,
            long gmailCount,
            String createdAt,
            String updatedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminCreateUserResponse(
            Long id,
            String username,
            String fullName,
            String email,
            String role,
            boolean enabled,
            boolean mustChangePassword,
            long gmailCount,
            String temporaryPassword,
            String createdAt,
            String updatedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminGmailMeta(
            Long id,
            String email,
            String displayName,
            String authType,
            String status,
            String createdAt,
            String lastVerifiedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminUserDetail(
            Long id,
            String username,
            String fullName,
            String email,
            String role,
            boolean enabled,
            boolean mustChangePassword,
            long gmailCount,
            List<AdminGmailMeta> gmailAccounts,
            String createdAt,
            String updatedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminResetPasswordResponse(
            Long userId,
            String username,
            String temporaryPassword,
            boolean mustChangePassword,
            String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminSettingItem(
            String key,
            String value,
            String type,
            Integer minValue,
            Integer maxValue,
            String description,
            String updatedByUsername,
            String updatedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyCountPoint(
            String date,
            long count
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminStatsResponse(
            long totalUsers,
            long activeUsers,
            long lockedUsers,
            long totalAdmins,
            long totalGmailAccounts,
            long gmailByOAuth2,
            long gmailByAppPassword,
            long gmailNeedsReauth,
            long registrationsToday,
            long registrationsLast7Days,
            long registrationsLast30Days,
            long failedLoginsLast24h,
            List<DailyCountPoint> mailsSentLast7DaysByDay
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminAuditLogItem(
            Long id,
            Long userId,
            String username,
            String action,
            String detail,
            String ipAddress,
            String createdAt
    ) {}
}

