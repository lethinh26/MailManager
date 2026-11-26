package com.mailapp.service;

import com.mailapp.dto.admin.AdminDtos.*;
import com.mailapp.model.User;

import java.util.List;

public interface AdminService {

    AdminCreateUserResponse createUser(User adminActor, AdminCreateUserRequest request, String ipAddress);

    PageResponse<AdminUserSummary> listUsers(String q, String role, Boolean enabled, int page, int size, String sortBy, String sortDir);

    AdminUserDetail getUserDetail(Long userId);

    AdminUserSummary lockUser(User adminActor, Long targetUserId, String ipAddress);

    AdminUserSummary unlockUser(User adminActor, Long targetUserId, String ipAddress);

    AdminUserSummary updateUserRole(User adminActor, Long targetUserId, String newRoleStr, String ipAddress);

    AdminResetPasswordResponse resetPassword(User adminActor, Long targetUserId, String ipAddress);
    AdminResetPasswordResponse resetPassword(User adminActor, Long targetUserId, String newPassword, String ipAddress);

    void deleteUser(User adminActor, Long targetUserId, String ipAddress);

    List<AdminSettingItem> getAllSettings();

    List<AdminSettingItem> updateSettings(User adminActor, AdminUpdateSettingsRequest request, String ipAddress);

    AdminStatsResponse getStats();

    PageResponse<AdminAuditLogItem> searchLogs(Long userId, String username, String action, String from, String to, int page, int size);

    String exportLogsCsv(Long userId, String username, String action, String from, String to);
}

