package com.mailapp.service;

import com.mailapp.dto.GmailAccountResponse.LinkOutcome;
import com.mailapp.exception.AppException;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.LinkIntent;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.repository.UserRepository;
import com.mailapp.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Dịch vụ nghiệp vụ liên kết đa phương thức (OAuth2 + App Password) cho cùng một hộp thư Gmail.
 * Thực hiện upsert trong transaction theo khóa chuẩn hóa (user_id, email_key),
 * đồng thời xử lý tranh chấp đồng thời (race condition) bằng ràng buộc UNIQUE ở DB và retry an toàn 1 lần.
 */
@Service
public class GmailAccountLinkService {

    private static final Logger log = LoggerFactory.getLogger(GmailAccountLinkService.class);

    public record UpsertResult(GmailAccount account, LinkOutcome outcome, String message) {}

    private final GmailAccountRepository gmailAccountRepository;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final TransactionTemplate requiresNewTx;

    @Autowired
    public GmailAccountLinkService(GmailAccountRepository gmailAccountRepository,
                                   SystemSettingService systemSettingService,
                                   AuditLogService auditLogService,
                                   @Autowired(required = false) PlatformTransactionManager txManager,
                                   UserRepository userRepository) {
        this.gmailAccountRepository = gmailAccountRepository;
        this.systemSettingService = systemSettingService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        if (txManager != null) {
            TransactionTemplate tpl = new TransactionTemplate(txManager);
            tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.requiresNewTx = tpl;
        } else {
            this.requiresNewTx = null;
        }
    }

    public GmailAccountLinkService(GmailAccountRepository gmailAccountRepository,
                                   SystemSettingService systemSettingService,
                                   AuditLogService auditLogService,
                                   PlatformTransactionManager txManager) {
        this(gmailAccountRepository, systemSettingService, auditLogService, txManager, null);
    }

    /**
     * Upsert phương thức App Password cho (currentUser, emailKey).
     * - Chưa có hộp thư: tạo mới với authType = APP_PASSWORD (outcome = CREATED).
     * - Đã có hộp thư: giữ nguyên id, displayName và refreshTokenEncrypted (nếu có OAuth2);
     *   thêm hoặc cập nhật appPasswordEncrypted (outcome = METHOD_ADDED hoặc METHOD_UPDATED).
     * - CREATE từ chối hộp thư đã tồn tại, UPSERT mới cho phép cập nhật.
     */
    public UpsertResult upsertAppPassword(User currentUser,
                                          String rawEmail,
                                          String encryptedPassword,
                                          String requestedDisplayName,
                                          String clientIp) {
        return upsertAppPassword(currentUser, rawEmail, encryptedPassword, requestedDisplayName, clientIp, LinkIntent.UPSERT);
    }

    public UpsertResult upsertAppPassword(User currentUser, String rawEmail, String encryptedPassword,
                                          String requestedDisplayName, String clientIp, LinkIntent intent) {
        String displayEmail = EmailNormalizer.normalizeDisplayEmail(rawEmail);
        String emailKey = EmailNormalizer.normalizeKey(displayEmail);
        String maskedEmail = MailCredentials.maskEmail(displayEmail);

        return executeWithRaceRetry(() -> doUpsertAppPassword(
                currentUser, displayEmail, emailKey, maskedEmail, encryptedPassword, requestedDisplayName, clientIp, intent));
    }

    /**
     * Upsert phương thức Google OAuth2 cho (user, emailKey).
     * - Chưa có hộp thư: tạo mới với authType = OAUTH2 (outcome = CREATED).
     * - Đã có hộp thư: giữ nguyên id, displayName và appPasswordEncrypted (nếu có App Password);
     *   thêm hoặc cập nhật refreshTokenEncrypted; đặt mặc định preferredMethod = OAUTH2 khi thêm OAuth2 lần đầu;
     *   xóa fallbackActive và khôi phục status = ACTIVE.
     * - CREATE từ chối hộp thư đã tồn tại, UPSERT mới cho phép cập nhật.
     */
    public UpsertResult upsertOAuth2(User user,
                                     String rawEmail,
                                     String encryptedRefreshToken,
                                     String googleDisplayName,
                                     String trustedAvatarUrl,
                                     LocalDateTime tokenExpiresAt) {
        return upsertOAuth2(user, rawEmail, encryptedRefreshToken, googleDisplayName,
                trustedAvatarUrl, tokenExpiresAt, null);
    }

    public UpsertResult upsertOAuth2(User user, String rawEmail, String encryptedRefreshToken,
                                    String googleDisplayName, String trustedAvatarUrl,
                                    LocalDateTime tokenExpiresAt, Long expectedAccountId) {
        return upsertOAuth2(user, rawEmail, encryptedRefreshToken, googleDisplayName, trustedAvatarUrl,
                tokenExpiresAt, expectedAccountId, LinkIntent.UPSERT);
    }

    public UpsertResult upsertOAuth2(User user, String rawEmail, String encryptedRefreshToken,
                                    String googleDisplayName, String trustedAvatarUrl,
                                    LocalDateTime tokenExpiresAt, Long expectedAccountId, LinkIntent intent) {
        String displayEmail = EmailNormalizer.normalizeDisplayEmail(rawEmail);
        String emailKey = EmailNormalizer.normalizeKey(displayEmail);
        String maskedEmail = MailCredentials.maskEmail(displayEmail);

        return executeWithRaceRetry(() -> doUpsertOAuth2(
                user, displayEmail, emailKey, maskedEmail, encryptedRefreshToken,
                googleDisplayName, trustedAvatarUrl, tokenExpiresAt, expectedAccountId, intent));
    }

    private UpsertResult doUpsertAppPassword(User currentUser,
                                             String displayEmail,
                                             String emailKey,
                                             String maskedEmail,
                                             String encryptedPassword,
                                             String requestedDisplayName,
                                             String clientIp, LinkIntent intent) {
        currentUser = lockAndValidateOwner(currentUser, AuthType.APP_PASSWORD);
        LocalDateTime now = LocalDateTime.now();
        Optional<GmailAccount> existingOpt = gmailAccountRepository.findByUserIdAndEmailKey(currentUser.getId(), emailKey);

        if (existingOpt.isPresent()) {
            if (intent == LinkIntent.CREATE) {
                throw new AppException(HttpStatus.CONFLICT, "GMAIL_DUPLICATE", "Gmail này đã liên kết.");
            }
            GmailAccount existing = existingOpt.get();
            boolean alreadyHadAppPass = existing.hasAppPassword();

            existing.setAppPasswordEncrypted(encryptedPassword);
            existing.setLastVerifiedAt(now);

            // Giữ nguyên id, displayName hiện có (chỉ điền nếu đang trống) và giữ nguyên OAuth2 token nếu có
            if ((existing.getDisplayName() == null || existing.getDisplayName().isBlank())
                    && requestedDisplayName != null && !requestedDisplayName.isBlank()) {
                existing.setDisplayName(requestedDisplayName.trim());
            }

            // Phương thức chính mặc định: OAUTH2 nếu có, ngược lại APP_PASSWORD
            if (!existing.hasOAuth()) {
                existing.setAuthType(AuthType.APP_PASSWORD);
                existing.setStatus(AccountStatus.ACTIVE);
                existing.setFallbackActive(false);
            } else if (existing.getAuthType() == null) {
                existing.setAuthType(AuthType.OAUTH2);
            }

            GmailAccount saved = saveAndFlushSafe(existing);
            LinkOutcome outcome = alreadyHadAppPass ? LinkOutcome.METHOD_UPDATED : LinkOutcome.METHOD_ADDED;
            String message = alreadyHadAppPass
                    ? "Đã cập nhật App Password cho hộp thư " + maskedEmail + " thành công."
                    : "Đã thêm phương thức App Password cho hộp thư " + maskedEmail
                    + " (dùng làm dự phòng cùng Google OAuth2).";

            auditLogService.log(currentUser.getId(),
                    alreadyHadAppPass ? "UPDATE_GMAIL_METHOD" : "ADD_GMAIL_METHOD",
                    (alreadyHadAppPass ? "Cập nhật App Password: " : "Thêm App Password dự phòng: ") + maskedEmail,
                    clientIp);
            log.info("Người dùng id={} {} (APP_PASSWORD) cho {}", currentUser.getId(), outcome, maskedEmail);
            return new UpsertResult(saved, outcome, message);
        }

        int maxGmail = systemSettingService.getMaxGmailPerUser();
        if (maxGmail > 0 && gmailAccountRepository.countByUserId(currentUser.getId()) >= maxGmail) {
            throw new AppException(HttpStatus.CONFLICT, "GMAIL_LIMIT_REACHED",
                    "Bạn đã đạt giới hạn tối đa số lượng tài khoản Gmail được liên kết (" + maxGmail + ").");
        }

        String displayName = (requestedDisplayName != null && !requestedDisplayName.isBlank())
                ? requestedDisplayName.trim()
                : displayEmail;

        GmailAccount account = new GmailAccount();
        account.setUser(currentUser);
        account.setEmail(displayEmail);
        account.setEmailKey(emailKey);
        account.setDisplayName(displayName);
        account.setAuthType(AuthType.APP_PASSWORD);
        account.setAppPasswordEncrypted(encryptedPassword);
        account.setLastVerifiedAt(now);
        account.setStatus(AccountStatus.ACTIVE);
        account.setFallbackActive(false);

        GmailAccount saved = saveAndFlushSafe(account);
        auditLogService.log(currentUser.getId(), "LINK_GMAIL",
                "Liên kết Gmail App Password: " + maskedEmail, clientIp);
        log.info("Người dùng id={} đã liên kết Gmail mới (APP_PASSWORD): {}", currentUser.getId(), maskedEmail);

        String message = "Đã liên kết hộp thư " + maskedEmail + " bằng App Password thành công.";
        return new UpsertResult(saved, LinkOutcome.CREATED, message);
    }

    private UpsertResult doUpsertOAuth2(User user,
                                        String displayEmail,
                                        String emailKey,
                                        String maskedEmail,
                                        String encryptedRefreshToken,
                                        String googleDisplayName,
                                        String trustedAvatarUrl,
                                        LocalDateTime tokenExpiresAt,
                                        Long expectedAccountId, LinkIntent intent) {
        user = lockAndValidateOwner(user, AuthType.OAUTH2);
        LocalDateTime now = LocalDateTime.now();
        Optional<GmailAccount> existingOpt = gmailAccountRepository.findByUserIdAndEmailKey(user.getId(), emailKey);
        if (expectedAccountId != null && (existingOpt.isEmpty()
                || !expectedAccountId.equals(existingOpt.get().getId()))) {
            throw new AppException(HttpStatus.CONFLICT, "ACCOUNT_MISMATCH",
                    "Tài khoản Google vừa cấp quyền không khớp hộp thư đã chọn hoặc hộp thư đã bị gỡ.");
        }

        if (existingOpt.isPresent()) {
            if (intent == LinkIntent.CREATE) {
                throw new AppException(HttpStatus.CONFLICT, "GMAIL_DUPLICATE", "Gmail này đã liên kết.");
            }
            GmailAccount existing = existingOpt.get();
            boolean alreadyHadOAuth = existing.hasOAuth();

            if ((!alreadyHadOAuth || existing.getStatus() == AccountStatus.NEEDS_REAUTH)
                    && (encryptedRefreshToken == null || encryptedRefreshToken.isBlank())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "MISSING_REFRESH_TOKEN",
                        "Google không cấp Refresh Token khi thêm phương thức Google OAuth2 cho " + maskedEmail
                                + ". Hãy thu hồi quyền cũ tại myaccount.google.com/permissions rồi thử lại.");
            }

            if (encryptedRefreshToken != null && !encryptedRefreshToken.isBlank()) {
                existing.setRefreshTokenEncrypted(encryptedRefreshToken);
            }

            // Giữ nguyên id, displayName hiện có (chỉ điền nếu trống) và GIỮ NGUYÊN appPasswordEncrypted!
            if ((existing.getDisplayName() == null || existing.getDisplayName().isBlank())
                    && googleDisplayName != null && !googleDisplayName.isBlank()) {
                existing.setDisplayName(googleDisplayName.trim());
            }
            if (trustedAvatarUrl != null && !trustedAvatarUrl.isBlank()) {
                existing.setAvatarUrl(trustedAvatarUrl.trim());
            }

            // Phương thức chính mặc định: OAUTH2 khi thêm OAuth2 vào hộp thư trước đó chỉ có App Password
            if (!alreadyHadOAuth) {
                existing.setAuthType(AuthType.OAUTH2);
            }
            existing.setStatus(AccountStatus.ACTIVE);
            existing.setFallbackActive(false);
            existing.setTokenExpiresAt(tokenExpiresAt);
            existing.setLastVerifiedAt(now);

            GmailAccount saved = saveAndFlushSafe(existing);
            LinkOutcome outcome = alreadyHadOAuth ? LinkOutcome.METHOD_UPDATED : LinkOutcome.METHOD_ADDED;

            String message;
            if (alreadyHadOAuth) {
                message = "Cập nhật ủy quyền Google OAuth2 thành công cho hộp thư " + maskedEmail + ".";
            } else {
                message = "Đã thêm phương thức Google OAuth2 cho hộp thư " + maskedEmail
                        + " (đặt làm phương thức chính, vẫn giữ App Password dự phòng).";
            }

            auditLogService.log(user.getId(), "OAUTH2_CALLBACK",
                    "outcome=" + outcome.name() + "; email=" + maskedEmail, (String) null);
            log.info("Người dùng id={} {} (OAUTH2) cho {}", user.getId(), outcome, maskedEmail);
            return new UpsertResult(saved, outcome, message);
        }

        int maxGmail = systemSettingService.getMaxGmailPerUser();
        if (maxGmail > 0 && gmailAccountRepository.countByUserId(user.getId()) >= maxGmail) {
            throw new AppException(HttpStatus.CONFLICT, "GMAIL_LIMIT_REACHED",
                    "Bạn đã đạt giới hạn tối đa số lượng Gmail liên kết (" + maxGmail + ").");
        }

        if (encryptedRefreshToken == null || encryptedRefreshToken.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "MISSING_REFRESH_TOKEN",
                    "Google không cấp Refresh Token cho tài khoản " + maskedEmail
                            + ". Vui lòng truy cập myaccount.google.com/permissions để hủy quyền Veyra Mail, sau đó thử lại.");
        }

        GmailAccount newAccount = new GmailAccount();
        newAccount.setUser(user);
        newAccount.setEmail(displayEmail);
        newAccount.setEmailKey(emailKey);
        newAccount.setDisplayName(googleDisplayName != null && !googleDisplayName.isBlank()
                ? googleDisplayName.trim() : displayEmail);
        if (trustedAvatarUrl != null && !trustedAvatarUrl.isBlank()) {
            newAccount.setAvatarUrl(trustedAvatarUrl.trim());
        }
        newAccount.setAuthType(AuthType.OAUTH2);
        newAccount.setRefreshTokenEncrypted(encryptedRefreshToken);
        newAccount.setStatus(AccountStatus.ACTIVE);
        newAccount.setFallbackActive(false);
        newAccount.setTokenExpiresAt(tokenExpiresAt);
        newAccount.setLastVerifiedAt(now);

        GmailAccount saved = saveAndFlushSafe(newAccount);
        auditLogService.log(user.getId(), "OAUTH2_CALLBACK",
                "outcome=CREATED; email=" + maskedEmail, (String) null);
        log.info("Liên kết thành công tài khoản Gmail OAuth2 mới: {}", maskedEmail);

        String message = "Tài khoản " + maskedEmail + " đã được liên kết thành công vào Veyra Mail.";
        return new UpsertResult(saved, LinkOutcome.CREATED, message);
    }

    private User lockAndValidateOwner(User user, AuthType method) {
        if (userRepository == null) {
            // Direct unit-test constructor; production always receives the repository.
            checkPasswordChanged(user);
            return user;
        }
        User current = userRepository.lockUserById(user.getId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng."));
        if (!current.isEnabled()) {
            throw new AppException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa.");
        }
        checkPasswordChanged(current);
        if (current.getRole() != Role.ADMIN && systemSettingService.isMaintenanceMode()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "MAINTENANCE_MODE", "Hệ thống đang bảo trì.");
        }
        boolean enabled = method == AuthType.OAUTH2
                ? systemSettingService.isOAuth2LinkEnabled()
                : systemSettingService.isAppPasswordLinkEnabled();
        if (!enabled) {
            throw new AppException(HttpStatus.FORBIDDEN, "FEATURE_DISABLED", "Phương thức liên kết Gmail đã bị tắt.");
        }
        return current;
    }

    private void checkPasswordChanged(User user) {
        if (user.isMustChangePassword()) {
            throw new AppException(HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                    "Tài khoản yêu cầu bắt buộc phải đổi mật khẩu trước khi liên kết Gmail.");
        }
    }

    private GmailAccount saveAndFlushSafe(GmailAccount account) {
        if (requiresNewTx != null) {
            GmailAccount saved = gmailAccountRepository.saveAndFlush(account);
            return saved != null ? saved : account;
        }
        GmailAccount saved = gmailAccountRepository.save(account);
        return saved != null ? saved : account;
    }

    private UpsertResult executeWithRaceRetry(Supplier<UpsertResult> action) {
        try {
            return runInTx(action);
        } catch (DataIntegrityViolationException raceEx) {
            // Do not retry arbitrary persistence failures (including an audit
            // insert failure). Only a collision on this link's unique key
            // can be resolved by reloading the existing account.
            boolean linkCollision = false;
            for (Throwable cause = raceEx; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && violation.getConstraintName() != null) {
                    String key = violation.getConstraintName().toLowerCase(java.util.Locale.ROOT);
                    linkCollision = key.contains("uk_user_gmail_key");
                    if (linkCollision) break;
                }
            }
            if (!linkCollision) throw raceEx;
            log.info("Phát hiện tranh chấp đồng thời (UNIQUE user_id + email_key), tự động thử gộp lại vào bản ghi hiện có.");
            return runInTx(action);
        }
    }

    private UpsertResult runInTx(Supplier<UpsertResult> action) {
        if (requiresNewTx != null) {
            return requiresNewTx.execute(status -> action.get());
        }
        return action.get();
    }
}

