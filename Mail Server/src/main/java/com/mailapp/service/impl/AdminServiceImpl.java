package com.mailapp.service.impl;

import com.mailapp.dto.admin.AdminDtos.*;
import com.mailapp.exception.AppException;
import com.mailapp.model.*;
import com.mailapp.oauth2.OAuth2Service;
import com.mailapp.repository.AuditLogRepository;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.repository.SystemSettingRepository;
import com.mailapp.repository.UserRepository;
import com.mailapp.service.AdminService;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Triển khai toàn bộ nghiệp vụ Quản trị hệ thống (M13, M14):
 * EP-AD1..AD8, EP-S1/S2, EP-ST1/ST2 + xuất CSV an toàn chống CSV Injection.
 */
@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%&*";
    private static final String ALL_PW_CHARS = UPPER + LOWER + DIGITS + SPECIAL;
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private record SettingSpec(String key, String type, String defaultValue, Integer min, Integer max, String description) {}

    private static final List<SettingSpec> SETTING_SPECS = List.of(
            new SettingSpec("ALLOW_REGISTRATION", "BOOLEAN", "true", null, null, "Cho phép người dùng tự đăng ký tài khoản mới"),
            new SettingSpec("ALLOW_OAUTH2_LINK", "BOOLEAN", "true", null, null, "Cho phép liên kết Gmail bằng Google OAuth2"),
            new SettingSpec("ALLOW_APP_PASSWORD_LINK", "BOOLEAN", "true", null, null, "Cho phép liên kết Gmail bằng App Password"),
            new SettingSpec("ALLOW_SEND_MAIL", "BOOLEAN", "true", null, null, "Cho phép gửi thư qua SMTP"),
            new SettingSpec("MAINTENANCE_MODE", "BOOLEAN", "false", null, null, "Chế độ bảo trì hệ thống (chỉ Admin đăng nhập được)"),
            new SettingSpec("MAX_GMAIL_PER_USER", "INTEGER", "5", 0, 50, "Số tài khoản Gmail tối đa mỗi người dùng (0 = không giới hạn)"),
            new SettingSpec("MAX_ATTACHMENT_SIZE_MB", "INTEGER", "25", 1, 50, "Kích thước tệp đính kèm tối đa (MB)"),
            new SettingSpec("MAIL_PAGE_SIZE", "INTEGER", "30", 10, 100, "Số thư mặc định trên mỗi trang"),
            new SettingSpec("LOGIN_MAX_FAILED_ATTEMPTS", "INTEGER", "5", 3, 20, "Số lần đăng nhập sai tối đa trước khi chặn tạm thời")
    );

    private static final Map<String, SettingSpec> SETTING_SPEC_MAP;
    static {
        Map<String, SettingSpec> m = new LinkedHashMap<>();
        for (SettingSpec s : SETTING_SPECS) m.put(s.key(), s);
        SETTING_SPEC_MAP = Collections.unmodifiableMap(m);
    }

    private final UserRepository userRepository;
    private final GmailAccountRepository gmailAccountRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final AuditLogRepository auditLogRepository;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;
    private final OAuth2Service oauth2Service;
    private final PasswordEncoder passwordEncoder;

    public AdminServiceImpl(UserRepository userRepository,
                            GmailAccountRepository gmailAccountRepository,
                            SystemSettingRepository systemSettingRepository,
                            AuditLogRepository auditLogRepository,
                            SystemSettingService systemSettingService,
                            AuditLogService auditLogService,
                            OAuth2Service oauth2Service,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.gmailAccountRepository = gmailAccountRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.auditLogRepository = auditLogRepository;
        this.systemSettingService = systemSettingService;
        this.auditLogService = auditLogService;
        this.oauth2Service = oauth2Service;
        this.passwordEncoder = passwordEncoder;
    }

    // ==================== EP-AD1..AD8: QUẢN LÝ NGƯỜI DÙNG ====================

    @Override
    @Transactional
    public AdminCreateUserResponse createUser(User adminActor, AdminCreateUserRequest request, String ipAddress) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new AppException(HttpStatus.CONFLICT, "USERNAME_TAKEN",
                    "Tên đăng nhập '" + username + "' đã tồn tại.");
        }

        Role role = parseRoleOrDefault(request.role(), Role.USER);

        String rawPassword;
        String returnedTempPassword;
        if (request.password() == null || request.password().isBlank()) {
            rawPassword = generateSecureTempPassword();
            returnedTempPassword = rawPassword;
        } else {
            validatePasswordPolicy(request.password());
            rawPassword = request.password();
            returnedTempPassword = null;
        }

        User user = new User();
        user.setUsername(username);
        user.setFullName(request.fullName().trim());
        user.setEmail(request.email().trim());
        user.setRole(role);
        user.setEnabled(true);
        user.setMustChangePassword(true); // Luôn bắt đổi mật khẩu khi đăng nhập lần đầu
        user.setPasswordHash(passwordEncoder.encode(rawPassword));

        User saved = userRepository.save(user);

        auditLogService.log(adminActor.getId(), "ADMIN_CREATE_USER",
                "Admin [" + adminActor.getUsername() + "] tạo tài khoản [" + saved.getUsername()
                        + "] (id=" + saved.getId() + ", role=" + saved.getRole() + ")",
                ipAddress);

        return new AdminCreateUserResponse(
                saved.getId(), saved.getUsername(), saved.getFullName(), saved.getEmail(),
                saved.getRole().name(), saved.isEnabled(), saved.isMustChangePassword(),
                0L, returnedTempPassword, saved.getCreatedAt(), saved.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummary> listUsers(String q, String roleStr, Boolean enabled,
                                                    int page, int size, String sortBy, String sortDir) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Role roleFilter = (roleStr != null && !roleStr.isBlank()) ? parseRoleStrict(roleStr) : null;

        String sortField = switch (sortBy != null ? sortBy : "createdAt") {
            case "id" -> "id";
            case "username" -> "username";
            case "role" -> "role";
            case "enabled" -> "enabled";
            default -> "createdAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(dir, sortField));

        String trimmedQ = (q != null && !q.isBlank()) ? q.trim() : null;
        Page<User> userPage = userRepository.searchAdminUsers(trimmedQ, roleFilter, enabled, pageable);

        List<AdminUserSummary> content = userPage.getContent().stream()
                .map(this::toUserSummary)
                .toList();

        return new PageResponse<>(content, userPage.getNumber(), userPage.getSize(),
                userPage.getTotalElements(), userPage.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetail getUserDetail(Long userId) {
        User user = findUserOrThrow(userId);
        List<AdminGmailMeta> gmails = gmailAccountRepository.findByUserId(userId).stream()
                .map(g -> new AdminGmailMeta(
                        g.getId(), g.getEmail(), g.getDisplayName(),
                        g.getAuthType() != null ? g.getAuthType().name() : null,
                        g.getStatus() != null ? g.getStatus().name() : null,
                        g.getCreatedAt(), g.getLastVerifiedAt()
                ))
                .toList();

        return new AdminUserDetail(
                user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getRole().name(), user.isEnabled(), user.isMustChangePassword(),
                gmails.size(), gmails, user.getCreatedAt(), user.getUpdatedAt()
        );
    }

    @Override
    @Transactional
    public AdminUserSummary lockUser(User adminActor, Long targetUserId, String ipAddress) {
        if (Objects.equals(adminActor.getId(), targetUserId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "SELF_ACTION_FORBIDDEN",
                    "Quản trị viên không thể tự khóa tài khoản của chính mình.");
        }
        User target = findUserOrThrow(targetUserId);
        ensureNotLastActiveAdmin(target, "khóa");

        target.setEnabled(false);
        User saved = userRepository.save(target);

        auditLogService.log(adminActor.getId(), "ADMIN_LOCK_USER",
                "Admin [" + adminActor.getUsername() + "] đã khóa tài khoản [" + saved.getUsername()
                        + "] (id=" + saved.getId() + ")",
                ipAddress);
        return toUserSummary(saved);
    }

    @Override
    @Transactional
    public AdminUserSummary unlockUser(User adminActor, Long targetUserId, String ipAddress) {
        User target = findUserOrThrow(targetUserId);
        target.setEnabled(true);
        User saved = userRepository.save(target);

        auditLogService.log(adminActor.getId(), "ADMIN_UNLOCK_USER",
                "Admin [" + adminActor.getUsername() + "] đã mở khóa tài khoản [" + saved.getUsername()
                        + "] (id=" + saved.getId() + ")",
                ipAddress);
        return toUserSummary(saved);
    }

    @Override
    @Transactional
    public AdminUserSummary updateUserRole(User adminActor, Long targetUserId, String newRoleStr, String ipAddress) {
        Role newRole = parseRoleStrict(newRoleStr);
        User target = findUserOrThrow(targetUserId);

        if (Objects.equals(adminActor.getId(), targetUserId) && newRole != Role.ADMIN) {
            throw new AppException(HttpStatus.BAD_REQUEST, "SELF_ACTION_FORBIDDEN",
                    "Quản trị viên không thể tự hạ quyền của chính mình.");
        }
        if (target.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
            ensureNotLastActiveAdmin(target, "hạ quyền");
        }

        Role oldRole = target.getRole();
        target.setRole(newRole);
        User saved = userRepository.save(target);

        auditLogService.log(adminActor.getId(), "ADMIN_CHANGE_ROLE",
                "Admin [" + adminActor.getUsername() + "] đổi vai trò [" + saved.getUsername()
                        + "] (id=" + saved.getId() + "): " + oldRole + " -> " + newRole,
                ipAddress);
        return toUserSummary(saved);
    }

    @Override
    @Transactional
    public AdminResetPasswordResponse resetPassword(User adminActor, Long targetUserId, String ipAddress) {
        User target = findUserOrThrow(targetUserId);
        String tempPassword = generateSecureTempPassword();

        target.setPasswordHash(passwordEncoder.encode(tempPassword));
        target.setMustChangePassword(true);
        userRepository.save(target);

        // TUYỆT ĐỐI không ghi mật khẩu vào audit log
        auditLogService.log(adminActor.getId(), "ADMIN_RESET_PASSWORD",
                "Admin [" + adminActor.getUsername() + "] đặt lại mật khẩu tạm cho [" + target.getUsername()
                        + "] (id=" + target.getId() + "), must_change_password=true",
                ipAddress);

        return new AdminResetPasswordResponse(
                target.getId(),
                target.getUsername(),
                tempPassword,
                true,
                "Đã tạo mật khẩu tạm mới (chỉ hiển thị một lần). Người dùng bắt buộc đổi mật khẩu ở lần đăng nhập kế tiếp."
        );
    }

    @Override
    @Transactional
    public void deleteUser(User adminActor, Long targetUserId, String ipAddress) {
        if (Objects.equals(adminActor.getId(), targetUserId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "SELF_ACTION_FORBIDDEN",
                    "Quản trị viên không thể tự xóa tài khoản của chính mình.");
        }
        User target = findUserOrThrow(targetUserId);
        ensureNotLastActiveAdmin(target, "xóa");

        String targetUsername = target.getUsername();

        // 1. Thu hồi OAuth2 token dạng best-effort cho toàn bộ Gmail OAuth2 của user
        List<GmailAccount> gmails = gmailAccountRepository.findByUserId(targetUserId);
        for (GmailAccount acc : gmails) {
            if (acc.getAuthType() == AuthType.OAUTH2) {
                try {
                    oauth2Service.revokeToken(acc);
                } catch (Exception ex) {
                    log.warn("Thu hồi OAuth2 token best-effort thất bại khi xóa user id={}, gmailId={}: {}",
                            targetUserId, acc.getId(), ex.getMessage());
                }
            }
        }

        // 2. Xóa cascade Gmail & tách tham chiếu FK trên audit_logs/system_settings trước khi xóa user
        gmailAccountRepository.deleteAll(gmails);
        gmailAccountRepository.flush();
        auditLogRepository.detachUser(targetUserId);
        systemSettingRepository.detachUpdatedBy(targetUserId);
        userRepository.delete(target);

        auditLogService.log(adminActor.getId(), "ADMIN_DELETE_USER",
                "Admin [" + adminActor.getUsername() + "] đã xóa tài khoản [" + targetUsername
                        + "] (id=" + targetUserId + ") cùng " + gmails.size() + " liên kết Gmail",
                ipAddress);
    }

    // ==================== EP-S1 / EP-S2: CẤU HÌNH HỆ THỐNG ====================

    @Override
    @Transactional(readOnly = true)
    public List<AdminSettingItem> getAllSettings() {
        Map<String, SystemSetting> existing = new HashMap<>();
        for (SystemSetting s : systemSettingRepository.findAll()) {
            existing.put(s.getKey(), s);
        }

        List<AdminSettingItem> result = new ArrayList<>();
        for (SettingSpec spec : SETTING_SPECS) {
            SystemSetting entity = existing.get(spec.key());
            String val = entity != null ? entity.getValue() : spec.defaultValue();
            String updatedBy = (entity != null && entity.getUpdatedBy() != null)
                    ? entity.getUpdatedBy().getUsername() : null;
            LocalDateTime updatedAt = entity != null ? entity.getUpdatedAt() : null;
            String desc = (entity != null && entity.getDescription() != null && !entity.getDescription().isBlank())
                    ? entity.getDescription() : spec.description();

            result.add(new AdminSettingItem(spec.key(), val, spec.type(), spec.min(), spec.max(),
                    desc, updatedBy, updatedAt));
        }
        return result;
    }

    @Override
    @Transactional
    public List<AdminSettingItem> updateSettings(User adminActor, AdminUpdateSettingsRequest request, String ipAddress) {
        Map<String, String> incoming = request.settings();
        if (incoming == null || incoming.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Không có cấu hình nào để cập nhật.");
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().trim() : "";
            SettingSpec spec = SETTING_SPEC_MAP.get(key);
            if (spec == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Khóa cấu hình không hợp lệ: " + key);
            }
            String rawVal = entry.getValue() != null ? entry.getValue().trim() : "";
            normalized.put(key, validateAndNormalizeSetting(spec, rawVal));
        }

        // Kiểm tra ràng buộc liên khóa: KHÔNG cho tắt cả OAuth2 và App Password cùng lúc
        boolean effectiveOAuth2 = normalized.containsKey("ALLOW_OAUTH2_LINK")
                ? Boolean.parseBoolean(normalized.get("ALLOW_OAUTH2_LINK"))
                : systemSettingService.isOAuth2LinkEnabled();
        boolean effectiveAppPass = normalized.containsKey("ALLOW_APP_PASSWORD_LINK")
                ? Boolean.parseBoolean(normalized.get("ALLOW_APP_PASSWORD_LINK"))
                : systemSettingService.isAppPasswordLinkEnabled();

        if (!effectiveOAuth2 && !effectiveAppPass) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Không được tắt đồng thời cả ALLOW_OAUTH2_LINK và ALLOW_APP_PASSWORD_LINK.");
        }

        // Lưu từng khóa, ghi updated_by, làm mới cache tức thì, ghi audit cũ -> mới
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            String key = entry.getKey();
            String newVal = entry.getValue();
            SettingSpec spec = SETTING_SPEC_MAP.get(key);

            SystemSetting entity = systemSettingRepository.findById(key)
                    .orElse(new SystemSetting(key, spec.defaultValue(), spec.description(), adminActor));
            String oldVal = entity.getValue();

            if (!Objects.equals(oldVal, newVal) || entity.getUpdatedBy() == null) {
                if (entity.getDescription() == null || entity.getDescription().isBlank()) {
                    entity.setDescription(spec.description());
                }
                entity.setValue(newVal);
                entity.setUpdatedBy(adminActor);
                systemSettingRepository.save(entity);
                systemSettingService.updateSetting(key, newVal, adminActor);

                if (!Objects.equals(oldVal, newVal)) {
                    auditLogService.log(adminActor.getId(), "ADMIN_CHANGE_SETTING",
                            "Admin [" + adminActor.getUsername() + "] đổi cấu hình " + key
                                    + ": " + oldVal + " -> " + newVal,
                            ipAddress);
                }
            }
        }
        systemSettingService.evictCache();

        return getAllSettings();
    }

    // ==================== EP-ST1 / EP-ST2: THỐNG KÊ & NHẬT KÝ ====================

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByEnabledTrue();
        long lockedUsers = userRepository.countByEnabledFalse();
        long totalAdmins = userRepository.countByRole(Role.ADMIN);

        long totalGmails = gmailAccountRepository.count();
        long gmailOAuth2 = gmailAccountRepository.countByAuthType(AuthType.OAUTH2);
        long gmailAppPass = gmailAccountRepository.countByAuthType(AuthType.APP_PASSWORD);
        long gmailNeedsReauth = gmailAccountRepository.countByStatus(AccountStatus.NEEDS_REAUTH);

        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime sevenDaysAgo = today.minusDays(6).atStartOfDay();
        LocalDateTime thirtyDaysAgo = today.minusDays(29).atStartOfDay();
        LocalDateTime last24h = LocalDateTime.now().minusHours(24);

        long regToday = userRepository.countByCreatedAtAfter(startOfToday);
        long reg7d = userRepository.countByCreatedAtAfter(sevenDaysAgo);
        long reg30d = userRepository.countByCreatedAtAfter(thirtyDaysAgo);
        long failedLogins24h = auditLogRepository.countByActionAndCreatedAtAfter("LOGIN_FAILED", last24h);

        // Gom số mail gửi 7 ngày gần nhất theo từng ngày
        Map<LocalDate, Long> sentByDate = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            sentByDate.put(today.minusDays(i), 0L);
        }
        List<AuditLog> sendLogs = auditLogRepository.findByActionAndCreatedAtGreaterThanEqual("SEND_MAIL", sevenDaysAgo);
        for (AuditLog a : sendLogs) {
            if (a.getCreatedAt() != null) {
                LocalDate d = a.getCreatedAt().toLocalDate();
                if (sentByDate.containsKey(d)) {
                    sentByDate.put(d, sentByDate.get(d) + 1L);
                }
            }
        }
        List<DailyCountPoint> dailyPoints = sentByDate.entrySet().stream()
                .map(e -> new DailyCountPoint(DAY_FMT.format(e.getKey()), e.getValue()))
                .toList();

        return new AdminStatsResponse(
                totalUsers, activeUsers, lockedUsers, totalAdmins,
                totalGmails, gmailOAuth2, gmailAppPass, gmailNeedsReauth,
                regToday, reg7d, reg30d, failedLogins24h, dailyPoints
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminAuditLogItem> searchLogs(Long userId, String username, String action,
                                                      String from, String to, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        LocalDateTime fromTime = parseDateBound(from, true);
        LocalDateTime toTime = parseDateBound(to, false);

        String cleanUser = (username != null && !username.isBlank()) ? username.trim() : null;
        String cleanAction = (action != null && !action.isBlank()) ? action.trim() : null;

        Page<AuditLog> logPage = auditLogRepository.searchLogs(
                userId, cleanUser, cleanAction, fromTime, toTime, PageRequest.of(safePage, safeSize));

        List<AdminAuditLogItem> items = logPage.getContent().stream()
                .map(this::toAuditItem)
                .toList();

        return new PageResponse<>(items, logPage.getNumber(), logPage.getSize(),
                logPage.getTotalElements(), logPage.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public String exportLogsCsv(Long userId, String username, String action, String from, String to) {
        LocalDateTime fromTime = parseDateBound(from, true);
        LocalDateTime toTime = parseDateBound(to, false);
        String cleanUser = (username != null && !username.isBlank()) ? username.trim() : null;
        String cleanAction = (action != null && !action.isBlank()) ? action.trim() : null;

        // Giới hạn tối đa 10.000 dòng cho 1 lần xuất CSV để bảo vệ RAM
        Page<AuditLog> logPage = auditLogRepository.searchLogs(
                userId, cleanUser, cleanAction, fromTime, toTime, PageRequest.of(0, 10_000));

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM cho Excel mở đúng tiếng Việt
        sb.append("ID,UserID,Username,Action,Detail,IPAddress,CreatedAt\r\n");

        for (AuditLog a : logPage.getContent()) {
            AdminAuditLogItem item = toAuditItem(a);
            sb.append(csvCell(item.id() != null ? item.id().toString() : "")).append(',')
              .append(csvCell(item.userId() != null ? item.userId().toString() : "")).append(',')
              .append(csvCell(item.username())).append(',')
              .append(csvCell(item.action())).append(',')
              .append(csvCell(item.detail())).append(',')
              .append(csvCell(item.ipAddress())).append(',')
              .append(csvCell(item.createdAt() != null ? item.createdAt().toString() : ""))
              .append("\r\n");
        }
        return sb.toString();
    }

    /**
     * Bảo vệ chống CSV Injection (Excel Formula Injection):
     * Nếu giá trị bắt đầu bằng =, +, -, @, tab hoặc CR -> thêm tiền tố dấu nháy đơn (') trước khi bọc ngoặc kép.
     */
    public static String csvCell(String value) {
        if (value == null) return "\"\"";
        String cleaned = value.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
        if (!cleaned.isEmpty()) {
            char first = cleaned.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                cleaned = "'" + cleaned;
            }
        }
        return "\"" + cleaned.replace("\"", "\"\"") + "\"";
    }

    // ==================== HELPERS ====================

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "Không tìm thấy người dùng với ID: " + id));
    }

    private void ensureNotLastActiveAdmin(User target, String actionVerb) {
        if (target.getRole() == Role.ADMIN && target.isEnabled()) {
            long activeAdmins = userRepository.countByRoleAndEnabledTrue(Role.ADMIN);
            if (activeAdmins <= 1) {
                throw new AppException(HttpStatus.CONFLICT, "LAST_ADMIN",
                        "Không thể " + actionVerb + " Quản trị viên đang hoạt động cuối cùng của hệ thống.");
            }
        }
    }

    private AdminUserSummary toUserSummary(User u) {
        long gmailCount = gmailAccountRepository.countByUserId(u.getId());
        return new AdminUserSummary(
                u.getId(), u.getUsername(), u.getFullName(), u.getEmail(),
                u.getRole().name(), u.isEnabled(), u.isMustChangePassword(),
                gmailCount, u.getCreatedAt(), u.getUpdatedAt()
        );
    }

    private AdminAuditLogItem toAuditItem(AuditLog a) {
        Long uid = a.getUser() != null ? a.getUser().getId() : null;
        String uname = a.getUser() != null ? a.getUser().getUsername() : "(system)";
        return new AdminAuditLogItem(a.getId(), uid, uname, a.getAction(), a.getDetail(), a.getIpAddress(), a.getCreatedAt());
    }

    private Role parseRoleOrDefault(String roleStr, Role defaultRole) {
        if (roleStr == null || roleStr.isBlank()) return defaultRole;
        return parseRoleStrict(roleStr);
    }

    private Role parseRoleStrict(String roleStr) {
        try {
            return Role.valueOf(roleStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Vai trò không hợp lệ (chỉ chấp nhận ADMIN hoặc USER): " + roleStr);
        }
    }

    private void validatePasswordPolicy(String password) {
        if (password.length() < 8 || password.length() > 128
                || !password.matches(".*[A-Za-z].*")
                || !password.matches(".*[0-9].*")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Mật khẩu phải từ 8 đến 128 ký tự, gồm ít nhất một chữ cái và một chữ số.");
        }
    }

    private String validateAndNormalizeSetting(SettingSpec spec, String rawVal) {
        if ("BOOLEAN".equals(spec.type())) {
            if ("true".equalsIgnoreCase(rawVal) || "false".equalsIgnoreCase(rawVal)) {
                return rawVal.toLowerCase(Locale.ROOT);
            }
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Giá trị của " + spec.key() + " phải là true hoặc false.");
        }
        try {
            int num = Integer.parseInt(rawVal);
            if ((spec.min() != null && num < spec.min()) || (spec.max() != null && num > spec.max())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Giá trị của " + spec.key() + " phải nằm trong khoảng [" + spec.min() + ", " + spec.max() + "].");
            }
            return String.valueOf(num);
        } catch (NumberFormatException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Giá trị của " + spec.key() + " phải là số nguyên hợp lệ.");
        }
    }

    private LocalDateTime parseDateBound(String input, boolean startOfDay) {
        if (input == null || input.isBlank()) return null;
        String s = input.trim();
        try {
            if (s.length() == 10) {
                LocalDate d = LocalDate.parse(s, DAY_FMT);
                return startOfDay ? d.atStartOfDay() : d.atTime(LocalTime.MAX);
            }
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Định dạng thời gian không hợp lệ (dùng yyyy-MM-dd hoặc ISO-8601): " + s);
        }
    }

    public static String generateSecureTempPassword() {
        List<Character> chars = new ArrayList<>(14);
        chars.add(UPPER.charAt(SECURE_RANDOM.nextInt(UPPER.length())));
        chars.add(UPPER.charAt(SECURE_RANDOM.nextInt(UPPER.length())));
        chars.add(LOWER.charAt(SECURE_RANDOM.nextInt(LOWER.length())));
        chars.add(LOWER.charAt(SECURE_RANDOM.nextInt(LOWER.length())));
        chars.add(DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length())));
        chars.add(DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length())));
        chars.add(SPECIAL.charAt(SECURE_RANDOM.nextInt(SPECIAL.length())));
        for (int i = chars.size(); i < 14; i++) {
            chars.add(ALL_PW_CHARS.charAt(SECURE_RANDOM.nextInt(ALL_PW_CHARS.length())));
        }
        Collections.shuffle(chars, SECURE_RANDOM);
        StringBuilder sb = new StringBuilder(chars.size());
        for (char c : chars) sb.append(c);
        return sb.toString();
    }
}
