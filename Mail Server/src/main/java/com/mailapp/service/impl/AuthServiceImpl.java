package com.mailapp.service.impl;

import com.mailapp.dto.*;
import com.mailapp.exception.AppException;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import com.mailapp.security.JwtProvider;
import com.mailapp.security.LoginRateLimiter;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.AuthService;
import com.mailapp.service.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai nghiệp vụ xác thực hệ thống:
 * 1. [EP-A2] Đăng nhập: Chống timing-attack, rate limit, kiểm tra bảo trì, phát hành JWT.
 * 2. [EP-A1] Đăng ký: Kiểm tra cờ ALLOW_REGISTRATION, rate limit IP, username trùng (409), role luôn là USER.
 * 3. [EP-A4] Đổi mật khẩu: BCrypt check mật khẩu cũ, chính sách mật khẩu, cập nhật must_change_password=false, cấp JWT mới.
 *
 * @author Lê Thanh Cường
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    // Hash BCrypt mẫu work-factor 10 để chạy phép tính thời gian thực khi user không tồn tại
    private static final String DUMMY_BCRYPT_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO.8H5L0o.Pq.jIqY5qE4q8o8y2h2n2vO";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            SystemSettingService systemSettingService,
            AuditLogService auditLogService,
            LoginRateLimiter loginRateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.systemSettingService = systemSettingService;
        this.auditLogService = auditLogService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Override
    // Rejected login attempts have no user writes; retain their audit record.
    @Transactional(noRollbackFor = AppException.class)
    public LoginResponse login(LoginRequest request, String clientIp) {
        String username = request.username().trim();
        String password = request.password();

        // 1. Kiểm tra giới hạn tần suất đăng nhập (Rate Limiter)
        loginRateLimiter.checkLimit(username, clientIp);

        if (!com.mailapp.security.PasswordPolicy.fitsBcrypt(password)) {
            loginRateLimiter.recordFailedAttempt(username, clientIp);
            auditLogService.log(null, "LOGIN_FAILED", "BAD_PASSWORD_LENGTH", clientIp);
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sai tài khoản hoặc mật khẩu");
        }

        // 2. Tìm kiếm người dùng theo tên đăng nhập HOẶC địa chỉ email (hỗ trợ đăng nhập bằng Gmail + mật khẩu)
        var userOptional = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmailIgnoreCase(username));

        if (userOptional.isEmpty()) {
            // Chống timing attack: thực hiện phép băm tốn thời gian giả lập
            passwordEncoder.matches(password, DUMMY_BCRYPT_HASH);
            loginRateLimiter.recordFailedAttempt(username, clientIp);
            auditLogService.log(null, "LOGIN_FAILED", "UNKNOWN_USER", clientIp);
            log.warn("Đăng nhập thất bại: Tên người dùng không tồn tại");
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sai tài khoản hoặc mật khẩu");
        }

        User user = userRepository.lockUserById(userOptional.get().getId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Sai tài khoản hoặc mật khẩu"));

        // 3. Kiểm tra mật khẩu bằng BCrypt
        boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());

        if (!passwordMatches) {
            loginRateLimiter.recordFailedAttempt(username, clientIp);
            auditLogService.log(user.getId(), "LOGIN_FAILED", "BAD_PASSWORD", clientIp);
            log.warn("Đăng nhập thất bại: Sai mật khẩu cho user id={}", user.getId());
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sai tài khoản hoặc mật khẩu");
        }

        // 4. Mật khẩu ĐÚNG -> Kiểm tra trạng thái tài khoản bị khóa (chỉ báo sau khi mật khẩu đúng)
        if (!user.isEnabled()) {
            auditLogService.log(user.getId(), "LOGIN_FAILED", "ACCOUNT_LOCKED", clientIp);
            log.warn("Đăng nhập thất bại: Tài khoản bị khóa id={}", user.getId());
            throw new AppException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Tài khoản của bạn đã bị khóa.");
        }

        // 5. Kiểm tra chế độ bảo trì hệ thống (MAINTENANCE_MODE)
        if (systemSettingService.isMaintenanceMode() && user.getRole() != Role.ADMIN) {
            auditLogService.log(user.getId(), "LOGIN_FAILED", "MAINTENANCE_MODE", clientIp);
            log.warn("Từ chối người dùng thông thường id={} trong thời gian bảo trì hệ thống", user.getId());
            throw new AppException(HttpStatus.FORBIDDEN, "MAINTENANCE_MODE",
                    "Hệ thống đang bảo trì, chỉ quản trị viên mới có thể đăng nhập.");
        }

        // 6. Tự động nâng cấp hash mật khẩu nếu thuật toán PasswordEncoder yêu cầu rehash
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            log.info("Nâng cấp hash mật khẩu cho user id={}", user.getId());
            user.setPasswordHash(passwordEncoder.encode(password));
            userRepository.save(user);
        }

        // 7. Đăng nhập thành công -> Xóa bộ đếm lỗi đăng nhập của IP
        loginRateLimiter.recordSuccess(username, clientIp);

        // 8. Phát hành JWT Access Token
        String accessToken = jwtProvider.generateToken(user);
        long expiresInSeconds = jwtProvider.getExpirationSeconds();

        // 9. Ghi Audit Log thành công
        auditLogService.log(user.getId(), "LOGIN", "SUCCESS", clientIp);
        log.info("Người dùng id={}, role={} đăng nhập thành công", user.getId(), user.getRole());

        return LoginResponse.of(accessToken, expiresInSeconds, UserDto.from(user));
    }

    @Override
    @Transactional
    public UserDto register(RegisterRequest request, String clientIp) {
        // 1. Kiểm tra chế độ bảo trì
        if (systemSettingService.isMaintenanceMode()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "MAINTENANCE_MODE",
                    "Hệ thống đang bảo trì, tạm thời không nhận đăng ký người dùng mới.");
        }

        // 2. Kiểm tra cờ cho phép đăng ký
        if (!systemSettingService.isRegistrationEnabled()) {
            throw new AppException(HttpStatus.FORBIDDEN, "REGISTRATION_DISABLED",
                    "Chức năng đăng ký tài khoản mới hiện đang bị tạm khóa.");
        }

        // 3. Áp dụng Rate Limit cho IP đăng ký
        loginRateLimiter.checkLimit("register", clientIp);

        String username = request.username().trim();
        String password = request.password();

        // 4. Kiểm tra chính sách mật khẩu (tối thiểu 8 ký tự, có chữ cái và chữ số)
        validatePasswordPolicy(password);

        // 5. Kiểm tra tên đăng nhập hoặc email đã tồn tại chưa (tránh trùng email giữa đăng ký thường & Google OAuth2)
        if (userRepository.findByUsername(username).isPresent()) {
            loginRateLimiter.recordFailedAttempt("register", clientIp);
            throw new AppException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Tên đăng nhập '" + username + "' đã được sử dụng.");
        }
        String normalizedEmail = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            loginRateLimiter.recordFailedAttempt("register", clientIp);
            throw new AppException(HttpStatus.CONFLICT, "EMAIL_TAKEN",
                    "Email '" + normalizedEmail + "' đã tồn tại trên hệ thống (có thể đã đăng ký hoặc đăng nhập bằng Google). "
                            + "Vui lòng quay lại và đăng nhập bằng Google hoặc tài khoản hiện có.");
        }

        // 6. Tạo người dùng mới (QUY TẮC BẢO MẬT: role luôn luôn là USER)
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setRole(Role.USER); // Bỏ qua bất kỳ giá trị role nào client gửi lên
        user.setEnabled(true);
        user.setMustChangePassword(false);

        User savedUser = userRepository.save(user);

        // 7. Ghi Audit Log và xóa bộ đếm rate limit
        auditLogService.log(savedUser.getId(), "REGISTER", "Đăng ký tài khoản mới thành công", clientIp);
        loginRateLimiter.recordSuccess("register", clientIp);
        log.info("Đã đăng ký tài khoản mới: id={}, username={}", savedUser.getId(), savedUser.getUsername());

        return UserDto.from(savedUser);
    }

    @Override
    // Validation failures precede all password writes and must retain failure audit.
    @Transactional(noRollbackFor = AppException.class)
    public ChangePasswordResponse changePassword(ChangePasswordRequest request, User currentUser, String clientIp) {
        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }

        // 1. Rate limit kiểm tra đổi mật khẩu (theo userId)
        String userKey = String.valueOf(currentUser.getId());
        loginRateLimiter.checkLimit("change-password", userKey);

        // Authentication happened before this transaction; never merge the principal
        // snapshot. Serialize with reset/lock/role changes and validate the latest hash.
        User user = userRepository.lockUserById(currentUser.getId())
                .filter(User::isEnabled)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Phiên đăng nhập không còn hợp lệ."));
        if (systemSettingService.isMaintenanceMode() && user.getRole() != Role.ADMIN) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "MAINTENANCE_MODE", "Hệ thống đang bảo trì.");
        }

        String currentPassword = request.currentPassword();
        String newPassword = request.newPassword();

        // 2. Kiểm tra mật khẩu hiện tại bằng BCrypt
        if (!com.mailapp.security.PasswordPolicy.fitsBcrypt(currentPassword)
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            loginRateLimiter.recordFailedAttempt("change-password", userKey);
            auditLogService.log(user.getId(), "CHANGE_PASSWORD_FAILED", "Mật khẩu hiện tại không chính xác", clientIp);
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Mật khẩu hiện tại sai");
        }

        // 3. Mật khẩu mới không được trùng mật khẩu cũ
        if (newPassword.equals(currentPassword)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Mật khẩu mới không được trùng với mật khẩu hiện tại.");
        }

        // 4. Kiểm tra chính sách mật khẩu
        validatePasswordPolicy(newPassword);

        // 5. Cập nhật mật khẩu mới và bỏ cờ must_change_password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // 6. Xóa bộ đếm rate limit và ghi audit (TUYỆT ĐỐI KHÔNG GHI MẬT KHẨU)
        loginRateLimiter.recordSuccess("change-password", userKey);
        auditLogService.log(user.getId(), "CHANGE_PASSWORD", "Đổi mật khẩu tài khoản thành công", clientIp);

        // 7. Phát hành JWT token mới phản ánh trạng thái mustChangePassword=false
        String newAccessToken = jwtProvider.generateToken(user);
        long expiresIn = jwtProvider.getExpirationSeconds();

        log.info("Người dùng id={} ({}) đã đổi mật khẩu thành công", user.getId(), user.getUsername());

        return new ChangePasswordResponse(
                "Đổi mật khẩu thành công.",
                newAccessToken,
                "Bearer",
                expiresIn,
                UserDto.from(user)
        );
    }

    private void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Mật khẩu phải có độ dài tối thiểu 8 ký tự.");
        }
        if (!com.mailapp.security.PasswordPolicy.fitsBcrypt(password)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Mật khẩu tối đa 72 byte UTF-8.");
        }
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasDigit = password.matches(".*[0-9].*");
        if (!hasLetter || !hasDigit) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Mật khẩu phải bao gồm cả chữ cái và chữ số.");
        }
    }
}
