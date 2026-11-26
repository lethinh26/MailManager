package com.mailapp.controller;

import com.mailapp.dto.admin.AdminDtos.*;
import com.mailapp.model.User;
import com.mailapp.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Controller Quản trị hệ thống (EP-AD1..AD8, EP-S1/S2, EP-ST1/ST2).
 * Chỉ cho phép ROLE_ADMIN; tuyệt đối không có API xem nội dung mail hay mật khẩu Gmail.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ==================== EP-AD1..AD8: USERS ====================

    @PostMapping("/users")
    public ResponseEntity<AdminCreateUserResponse> createUser(
            @AuthenticationPrincipal User adminActor,
            @Valid @RequestBody AdminCreateUserRequest request,
            HttpServletRequest httpRequest) {
        AdminCreateUserResponse created = adminService.createUser(adminActor, request, extractIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserSummary>> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        String effectiveQ = (q != null && !q.isBlank()) ? q : username;
        return ResponseEntity.ok(adminService.listUsers(effectiveQ, role, enabled, page, size, sortBy, sortDir));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserDetail> getUserDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUserDetail(id));
    }

    @PutMapping("/users/{id}/lock")
    public ResponseEntity<AdminUserSummary> lockUser(
            @AuthenticationPrincipal User adminActor,
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminService.lockUser(adminActor, id, extractIp(httpRequest)));
    }

    @PutMapping("/users/{id}/unlock")
    public ResponseEntity<AdminUserSummary> unlockUser(
            @AuthenticationPrincipal User adminActor,
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminService.unlockUser(adminActor, id, extractIp(httpRequest)));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<AdminUserSummary> updateUserRole(
            @AuthenticationPrincipal User adminActor,
            @PathVariable Long id,
            @Valid @RequestBody AdminRoleUpdateRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminService.updateUserRole(adminActor, id, request.role(), extractIp(httpRequest)));
    }

    @PutMapping("/users/{id}/reset-password")
    public ResponseEntity<AdminResetPasswordResponse> resetPassword(
            @AuthenticationPrincipal User adminActor,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        AdminResetPasswordResponse res = request == null
                ? adminService.resetPassword(adminActor, id, extractIp(httpRequest))
                : adminService.resetPassword(adminActor, id, request.newPassword(), extractIp(httpRequest));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(res);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @AuthenticationPrincipal User adminActor,
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        adminService.deleteUser(adminActor, id, extractIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    // ==================== EP-S1 / EP-S2: SETTINGS ====================

    @GetMapping("/settings")
    public ResponseEntity<List<AdminSettingItem>> getSettings() {
        return ResponseEntity.ok(adminService.getAllSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<List<AdminSettingItem>> updateSettings(
            @AuthenticationPrincipal User adminActor,
            @Valid @RequestBody AdminUpdateSettingsRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminService.updateSettings(adminActor, request, extractIp(httpRequest)));
    }

    // ==================== EP-ST1 / EP-ST2: STATS & LOGS ====================

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/logs")
    public ResponseEntity<PageResponse<AdminAuditLogItem>> getLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.searchLogs(userId, username, action, from, to, page, size));
    }

    @GetMapping(value = "/logs/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportLogsCsv(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String csv = adminService.exportLogsCsv(userId, username, action, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.csv\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private String extractIp(HttpServletRequest req) {
        return req != null && req.getRemoteAddr() != null ? req.getRemoteAddr() : "unknown";
    }
}

