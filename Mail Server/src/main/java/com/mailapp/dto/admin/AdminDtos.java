package com.mailapp.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Các DTO cho toàn bộ module Quản trị hệ thống (EP-AD1..AD8, EP-S1/S2, EP-ST1/ST2).
 * Ranh giới bảo mật: KHÔNG có bất kỳ trường mật khẩu Gmail, token hay nội dung thư nào.
 */
public final class AdminDtos {

    private AdminDtos() {}

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record AdminCreateUserRequest(
            @NotBlank(message = "Tên đăng nhập không được để trống.")
            @Size(min = 3, max = 64, message = "Tên đăng nhập phải từ 3 đến 64 ký tự.")
            @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Tên đăng nhập chỉ được gồm chữ cái, chữ số và dấu gạch dưới (_).")
            String username,

            @NotBlank(message = "Họ và tên không được để trống.")
            @Size(max = 255, message = "Họ và tên tối đa 255 ký tự.")
            String fullName,

            @NotBlank(message = "Email không được để trống.")
            @Email(message = "Định dạng email không hợp lệ.")
            @Size(max = 255, message = "Email tối đa 255 ký tự.")
            String email,

            String role,

            /** Tùy chọn: để trống sẽ tự sinh mật khẩu tạm ngẫu nhiên an toàn. */
            String password
    ) {}

    public record AdminUserSummary(
            Long id,
            String username,
            String fullName,
            String email,
            String role,
            boolean enabled,
            boolean mustChangePassword,
            long gmailCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

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
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /** Chỉ chứa metadata tài khoản Gmail — TUYỆT ĐỐI KHÔNG chứa mật khẩu hay OAuth token. */
    public record AdminGmailMeta(
            Long id,
            String email,
            String displayName,
            String authType,
            String status,
            LocalDateTime createdAt,
            LocalDateTime lastVerifiedAt
    ) {}

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
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record AdminRoleUpdateRequest(
            @NotBlank(message = "Vai trò không được để trống.")
            String role
    ) {}

    /** Optional reset body. An absent body retains generated-password behavior. */
    public record AdminResetPasswordRequest(
            @NotBlank(message = "Mật khẩu mới không được để trống.")
            @Size(min = 8, max = 128, message = "Mật khẩu mới phải từ 8 đến 128 ký tự.")
            String newPassword
    ) {
        @Override
        public String toString() { return "AdminResetPasswordRequest[newPassword=***]"; }
    }

    public record AdminResetPasswordResponse(
            Long userId,
            String username,
            String temporaryPassword,
            boolean mustChangePassword,
            String message
    ) {}

    public record AdminSettingItem(
            String key,
            String value,
            String type,
            Integer minValue,
            Integer maxValue,
            String description,
            String updatedByUsername,
            LocalDateTime updatedAt
    ) {
        @com.fasterxml.jackson.annotation.JsonProperty("updatedBy")
        public String updatedBy() { return updatedByUsername; }
    }

    public record AdminUpdateSettingsRequest(
            @NotEmpty(message = "Danh sách cấu hình cần cập nhật không được để trống.")
            Map<String, String> settings
    ) {}

    public record DailyCountPoint(
            String date,
            long count
    ) {}

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

    public record AdminAuditLogItem(
            Long id,
            Long userId,
            String username,
            String action,
            String detail,
            String ipAddress,
            LocalDateTime createdAt,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            String mailEventId,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            Long mailActorId
    ) {}
}

