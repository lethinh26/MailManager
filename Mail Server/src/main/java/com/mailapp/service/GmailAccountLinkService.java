package com.mailapp.service;

import com.mailapp.dto.GmailAccountResponse.LinkOutcome;
import com.mailapp.exception.AppException;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.User;
import com.mailapp.repository.GmailAccountRepository;
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
    private final TransactionTemplate requiresNewTx;

    @Autowired
    public GmailAccountLinkService(GmailAccountRepository gmailAccountRepository,
                                   SystemSettingService systemSettingService,
                                   AuditLogService auditLogService,
                                   @Autowired(required = false) PlatformTransactionManager txManager) {
        this.gmailAccountRepository = gmailAccountRepository;
        this.systemSettingService = systemSettingService;
        this.auditLogService = auditLogService;
        if (txManager != null) {
            TransactionTemplate tpl = new TransactionTemplate(txManager);
            tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.requiresNewTx = tpl;
        } else {
            this.requiresNewTx = null;
        }
    }

    /**
     * Upsert phương thức App Password cho (currentUser, emailKey).
     * - Chưa có hộp thư: tạo mới với authType = APP_PASSWORD (outcome = CREATED).
     * - Đã có hộp thư: giữ nguyên id, displayName và refreshTokenEncrypted (nếu có OAuth2);
     *   thêm hoặc cập nhật appPasswordEncrypted (outcome = METHOD_ADDED hoặc METHOD_UPDATED).
     * - Không bao giờ trả lỗi trùng email.
     */
    public UpsertResult upsertAppPassword(User currentUser,
                                          String rawEmail,
                                          String encryptedPassword,
                                          String requestedDisplayName,
                                          String clientIp) {
        String displayEmail = EmailNormalizer.normalizeDisplayEmail(rawEmail);
        String emailKey = EmailNormalizer.normalizeKey(displayEmail);
        String maskedEmail = MailCredentials.maskEmail(displayEmail);

        return executeWithRaceRetry(() -> doUpsertAppPassword(
                currentUser, displayEmail, emailKey, maskedEmail, encryptedPassword, requestedDisplayName, clientIp));
    }

    /**
     * Upsert phương thức Google OAuth2 cho (user, emailKey).
     * - Chưa có hộp thư: tạo mới với authType = OAUTH2 (outcome = CREATED).
     * - Đã có hộp thư: giữ nguyên id, displayName và appPasswordEncrypted (nếu có App Password);
     *   thêm hoặc cập nhật refreshTokenEncrypted; đặt mặc định preferredMethod = OAUTH2 khi thêm OAuth2 lần đầu;
     *   xóa fallbackActive và khôi phục status = ACTIVE.
     * - Không bao giờ trả lỗi trùng email.
     */
    public UpsertResult upsertOAuth2(User user,
                                     String rawEmail,
                                     String encryptedRefreshToken,
                                     String googleDisplayName,
                                     String trustedAvatarUrl,
                                     LocalDateTime tokenExpiresAt) {
        String displayEmail = EmailNormalizer.normalizeDisplayEmail(rawEmail);
        String emailKey = EmailNormalizer.normalizeKey(displayEmail);
        String maskedEmail = MailCredentials.maskEmail(displayEmail);

        return executeWithRaceRetry(() -> doUpsertOAuth2(
                user, displayEmail, emailKey, maskedEmail, encryptedRefreshToken,
                googleDisplayName, trustedAvatarUrl, tokenExpiresAt));
    }

    private UpsertResult doUpsertAppPassword(User currentUser,
                                             String displayEmail,
                                             String emailKey,
                                             String maskedEmail,
                                             String encryptedPassword,
                                             String requestedDisplayName,
                                             String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        Optional<GmailAccount> existingOpt = gmailAccountRepository.findByUserIdAndEmailKey(currentUser.getId(), emailKey);

        if (existingOpt.isPresent()) {
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
                                        LocalDateTime tokenExpiresAt) {
        LocalDateTime now = LocalDateTime.now();
        Optional<GmailAccount> existingOpt = gmailAccountRepository.findByUserIdAndEmailKey(user.getId(), emailKey);

        if (existingOpt.isPresent()) {
            GmailAccount existing = existingOpt.get();
            boolean alreadyHadOAuth = existing.hasOAuth();

            if (!alreadyHadOAuth && (encryptedRefreshToken == null || encryptedRefreshToken.isBlank())) {
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

            auditLogService.log(user.getId(),
                    alreadyHadOAuth ? "UPDATE_GMAIL_METHOD" : "ADD_GMAIL_METHOD",
                    (alreadyHadOAuth ? "Cập nhật liên kết OAuth2: " : "Thêm phương thức OAuth2: ") + maskedEmail,
                    null);
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
        auditLogService.log(user.getId(), "LINK_GMAIL", "Liên kết Gmail OAuth2: " + maskedEmail, null);
        log.info("Liên kết thành công tài khoản Gmail OAuth2 mới: {}", maskedEmail);

        String message = "Tài khoản " + maskedEmail + " đã được liên kết thành công vào Veyra Mail.";
        return new UpsertResult(saved, LinkOutcome.CREATED, message);
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

