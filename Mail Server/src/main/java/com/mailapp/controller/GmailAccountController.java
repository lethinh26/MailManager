package com.mailapp.controller;

import com.mailapp.dto.GmailAccountResponse;
import com.mailapp.dto.GmailAccountResponse.LinkOutcome;
import com.mailapp.dto.LinkAppPasswordRequest;
import com.mailapp.dto.PreferredMethodRequest;
import com.mailapp.exception.AppException;
import com.mailapp.mail.MailConnectionFactory;
import com.mailapp.mail.model.ConnectionTestResult;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.User;
import com.mailapp.oauth2.OAuth2Service;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.GmailAccountLinkService;
import com.mailapp.service.SystemSettingService;
import com.mailapp.util.EmailNormalizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller quản lý tài khoản Gmail liên kết của người dùng (EP-G1, EP-G2, EP-G3 và các endpoint mở rộng đa phương thức).
 *
 * @author Lê Thanh Cường
 */
@RestController
@RequestMapping("/api/gmail-accounts")
@Tag(name = "Gmail Accounts", description = "Quản lý hộp thư Gmail liên kết và đa phương thức đăng nhập (Google OAuth2 + App Password)")
public class GmailAccountController {

    private static final Logger log = LoggerFactory.getLogger(GmailAccountController.class);

    private final GmailAccountRepository gmailAccountRepository;
    private final OAuth2Service oauth2Service;
    private final AuditLogService auditLogService;
    private final SystemSettingService systemSettingService;
    private final MailConnectionFactory mailConnectionFactory;
    private final AesCipher aesCipher;
    private final GmailAccountLinkService linkService;

    public GmailAccountController(GmailAccountRepository gmailAccountRepository,
                                  OAuth2Service oauth2Service,
                                  AuditLogService auditLogService,
                                  SystemSettingService systemSettingService,
                                  MailConnectionFactory mailConnectionFactory,
                                  AesCipher aesCipher) {
        this(gmailAccountRepository, oauth2Service, auditLogService, systemSettingService,
                mailConnectionFactory, aesCipher,
                new GmailAccountLinkService(gmailAccountRepository, systemSettingService, auditLogService, null));
    }

    @Autowired
    public GmailAccountController(GmailAccountRepository gmailAccountRepository,
                                  OAuth2Service oauth2Service,
                                  AuditLogService auditLogService,
                                  SystemSettingService systemSettingService,
                                  MailConnectionFactory mailConnectionFactory,
                                  AesCipher aesCipher,
                                  GmailAccountLinkService linkService) {
        this.gmailAccountRepository = gmailAccountRepository;
        this.oauth2Service = oauth2Service;
        this.auditLogService = auditLogService;
        this.systemSettingService = systemSettingService;
        this.mailConnectionFactory = mailConnectionFactory;
        this.aesCipher = aesCipher;
        this.linkService = linkService;
    }

    /**
     * [EP-G1] Lấy danh sách tài khoản Gmail của chính người dùng đang đăng nhập.
     * Trả thêm methods (hasOAuth, hasAppPassword), preferredMethod, activeMethod, fallbackActive, status.
     * Tuyệt đối không trả về App Password hay Refresh Token.
     */
    @GetMapping
    @Operation(summary = "[EP-G1] Lấy danh sách hộp thư Gmail liên kết",
            description = "Trả danh sách hộp thư của người dùng hiện tại kèm methods, preferredMethod, activeMethod, fallbackActive, status. Không bao giờ trả token hay mật khẩu.")
    public ResponseEntity<List<GmailAccountResponse>> getMyGmailAccounts(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }

        List<GmailAccountResponse> accounts = gmailAccountRepository.findByUserId(currentUser.getId())
                .stream()
                .map(GmailAccountResponse::from)
                .toList();

        return ResponseEntity.ok(accounts);
    }

    /**
     * [EP-G2] Liên kết hoặc thêm/cập nhật phương thức App Password 16 ký tự cho một hộp thư Gmail.
     * Tìm theo (user_id, email_key). Nếu hộp thư đã liên kết Google OAuth2 thì thêm App Password vào cùng dòng
     * (giữ nguyên id, displayName và OAuth2 token) và trả outcome = METHOD_ADDED, không bao giờ trả lỗi trùng.
     */
    @PostMapping("/app-password")
    @Operation(summary = "[EP-G2] Liên kết hoặc bổ sung App Password cho hộp thư Gmail",
            description = "Kiểm tra IMAP thật qua IMAPS 993 rồi upsert theo (user_id, email_key). Trả outcome = CREATED | METHOD_ADDED | METHOD_UPDATED.")
    public ResponseEntity<GmailAccountResponse> linkGmailWithAppPassword(
            @Valid @RequestBody LinkAppPasswordRequest body,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {

        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }

        if (!systemSettingService.isAppPasswordLinkEnabled()) {
            throw new AppException(HttpStatus.FORBIDDEN, "FEATURE_DISABLED",
                    "Tính năng liên kết Gmail bằng App Password hiện đang bị tạm tắt bởi quản trị viên.");
        }

        String displayEmail = EmailNormalizer.normalizeDisplayEmail(body.email());
        String emailKey = EmailNormalizer.normalizeKey(displayEmail);
        String cleanPassword = body.appPassword().replaceAll("\\s+", "");
        if (cleanPassword.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "App Password không được để trống.");
        }

        // Chỉ kiểm tra giới hạn MAX_GMAIL_PER_USER nếu đây là hộp thư mới (chưa tồn tại email_key)
        boolean exists = gmailAccountRepository.findByUserIdAndEmailKey(currentUser.getId(), emailKey).isPresent();
        if (!exists) {
            int maxGmail = systemSettingService.getMaxGmailPerUser();
            if (maxGmail > 0 && gmailAccountRepository.countByUserId(currentUser.getId()) >= maxGmail) {
                throw new AppException(HttpStatus.CONFLICT, "GMAIL_LIMIT_REACHED",
                        "Bạn đã đạt giới hạn tối đa số lượng tài khoản Gmail được liên kết (" + maxGmail + ").");
            }
        }

        // Thử kết nối IMAP thật trước khi mã hóa & lưu vào DB
        ConnectionTestResult testResult = mailConnectionFactory.testImap(
                MailCredentials.ofAppPassword(displayEmail.toLowerCase(Locale.ROOT), cleanPassword)
        );
        if (testResult == null || !testResult.isSuccessful()) {
            ConnectionTestResult.Status st = testResult != null ? testResult.status() : ConnectionTestResult.Status.NETWORK_ERROR;
            String msg = testResult != null ? testResult.message() : "Không thể kiểm tra kết nối IMAP tới Gmail.";
            switch (st) {
                case AUTH_FAILED -> throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "GMAIL_AUTH_FAILED", msg);
                case IMAP_DISABLED -> throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "IMAP_DISABLED", msg);
                case TWO_STEP_VERIFICATION_REQUIRED -> throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "TWO_STEP_VERIFICATION_REQUIRED", msg);
                default -> throw new AppException(HttpStatus.BAD_GATEWAY, "GMAIL_CONNECTION_ERROR", msg);
            }
        }

        String encryptedPassword = aesCipher.encrypt(cleanPassword);
        String clientIp = request.getRemoteAddr();

        GmailAccountLinkService.UpsertResult upsert = linkService.upsertAppPassword(
                currentUser, displayEmail, encryptedPassword, body.displayName(), clientIp);

        HttpStatus responseStatus = upsert.outcome() == LinkOutcome.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(responseStatus)
                .body(GmailAccountResponse.from(upsert.account(), upsert.outcome(), upsert.message()));
    }

    /**
     * [MỞ RỘNG NGOÀI 27 ENDPOINT GỐC] Đổi phương thức đăng nhập chính (preferredMethod) cho hộp thư Gmail.
     */
    @PutMapping("/{id}/preferred-method")
    @Operation(summary = "[Mở rộng ngoài 27] Đổi phương thức đăng nhập chính (OAUTH2 / APP_PASSWORD)",
            description = "Endpoint mở rộng ngoài 27 endpoint gốc. Chuyển preferredMethod giữa OAUTH2 và APP_PASSWORD cho hộp thư đã có cả hai phương thức.")
    public ResponseEntity<GmailAccountResponse> updatePreferredMethod(
            @PathVariable Long id,
            @Valid @RequestBody PreferredMethodRequest body,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {

        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }

        GmailAccount account = gmailAccountRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản Gmail với ID: " + id));

        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Bạn không có quyền thao tác trên tài khoản Gmail này.");
        }

        AuthType desired = body.preferredMethod();
        if (desired == AuthType.OAUTH2 && !account.hasOAuth()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "METHOD_NOT_LINKED",
                    "Hộp thư này chưa liên kết phương thức Google OAuth2.");
        }
        if (desired == AuthType.APP_PASSWORD && !account.hasAppPassword()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "METHOD_NOT_LINKED",
                    "Hộp thư này chưa có phương thức App Password.");
        }

        account.setAuthType(desired);
        if (desired == AuthType.APP_PASSWORD) {
            account.setFallbackActive(false);
        }

        GmailAccount saved = gmailAccountRepository.save(account);
        GmailAccount target = saved != null ? saved : account;
        String maskedEmail = MailCredentials.maskEmail(target.getEmail());
        auditLogService.log(currentUser.getId(), "CHANGE_PREFERRED_METHOD",
                "Đổi phương thức chính sang " + desired + " cho " + maskedEmail, request.getRemoteAddr());

        String label = desired == AuthType.OAUTH2 ? "Google OAuth2" : "App Password";
        String msg = "Đã đặt " + label + " làm phương thức đăng nhập chính cho hộp thư " + maskedEmail + ".";
        return ResponseEntity.ok(GmailAccountResponse.from(target, null, msg));
    }

    /**
     * [MỞ RỘNG NGOÀI 27 ENDPOINT GỐC] Gỡ một phương thức đăng nhập (OAUTH2 hoặc APP_PASSWORD) khỏi hộp thư Gmail.
     * - Gỡ OAUTH2: thu hồi token Google (best-effort) và xóa refresh token.
     * - Gỡ APP_PASSWORD: xóa mật khẩu ứng dụng đã mã hóa.
     * - Gỡ phương thức cuối cùng: yêu cầu tham số confirmUnlink=true, sau đó xóa luôn liên kết hộp thư.
     */
    @DeleteMapping("/{id}/methods/{method}")
    @Operation(summary = "[Mở rộng ngoài 27] Gỡ phương thức đăng nhập khỏi hộp thư Gmail",
            description = "Endpoint mở rộng ngoài 27 endpoint gốc. Hỗ trợ method=OAUTH2 hoặc APP_PASSWORD. Nếu gỡ phương thức cuối cùng bắt buộc gửi confirmUnlink=true.")
    public ResponseEntity<Map<String, Object>> removeLoginMethod(
            @PathVariable Long id,
            @PathVariable String method,
            @RequestParam(name = "confirmUnlink", defaultValue = "false") boolean confirmUnlink,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {

        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }

        AuthType targetMethod;
        try {
            targetMethod = AuthType.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Phương thức không hợp lệ. Chỉ chấp nhận OAUTH2 hoặc APP_PASSWORD.");
        }

        GmailAccount account = gmailAccountRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản Gmail với ID: " + id));

        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Bạn không có quyền thao tác trên tài khoản Gmail này.");
        }

        if (targetMethod == AuthType.OAUTH2 && !account.hasOAuth()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "METHOD_NOT_LINKED",
                    "Hộp thư này không có phương thức Google OAuth2 để gỡ.");
        }
        if (targetMethod == AuthType.APP_PASSWORD && !account.hasAppPassword()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "METHOD_NOT_LINKED",
                    "Hộp thư này không có phương thức App Password để gỡ.");
        }

        boolean isLastMethod = (targetMethod == AuthType.OAUTH2 && !account.hasAppPassword())
                || (targetMethod == AuthType.APP_PASSWORD && !account.hasOAuth());

        String maskedEmail = MailCredentials.maskEmail(account.getEmail());
        String clientIp = request.getRemoteAddr();

        if (isLastMethod) {
            if (!confirmUnlink) {
                throw new AppException(HttpStatus.CONFLICT, "CONFIRM_UNLINK_REQUIRED",
                        "Đây là phương thức đăng nhập cuối cùng của hộp thư " + maskedEmail
                                + ". Hãy xác nhận với confirmUnlink=true để hủy toàn bộ liên kết.");
            }
            if (targetMethod == AuthType.OAUTH2) {
                revokeBestEffort(account, currentUser.getId(), maskedEmail, clientIp);
            }
            gmailAccountRepository.delete(account);
            auditLogService.log(currentUser.getId(), "UNLINK_GMAIL",
                    "Gỡ phương thức cuối cùng (" + targetMethod + ") và hủy liên kết: " + maskedEmail, clientIp);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("unlinked", true);
            body.put("removedMethod", targetMethod.name());
            body.put("message", "Đã gỡ phương thức cuối cùng và hủy liên kết hộp thư " + maskedEmail + ".");
            return ResponseEntity.ok(body);
        }

        if (targetMethod == AuthType.OAUTH2) {
            revokeBestEffort(account, currentUser.getId(), maskedEmail, clientIp);
            account.setRefreshTokenEncrypted(null);
            account.setTokenExpiresAt(null);
            account.setAuthType(AuthType.APP_PASSWORD);
            account.setFallbackActive(false);
            account.setStatus(AccountStatus.ACTIVE);
        } else {
            account.setAppPasswordEncrypted(null);
            account.setAuthType(AuthType.OAUTH2);
            account.setFallbackActive(false);
        }

        GmailAccount saved = gmailAccountRepository.save(account);
        GmailAccount updated = saved != null ? saved : account;
        auditLogService.log(currentUser.getId(), "REMOVE_GMAIL_METHOD",
                "Đã gỡ phương thức " + targetMethod + " cho " + maskedEmail, clientIp);

        String removedLabel = targetMethod == AuthType.OAUTH2 ? "Google OAuth2" : "App Password";
        String remainLabel = updated.getPreferredMethod() == AuthType.OAUTH2 ? "Google OAuth2" : "App Password";
        String msg = "Đã gỡ phương thức " + removedLabel + " khỏi hộp thư " + maskedEmail
                + ". Hộp thư tiếp tục sử dụng " + remainLabel + ".";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unlinked", false);
        body.put("removedMethod", targetMethod.name());
        body.put("message", msg);
        body.put("account", GmailAccountResponse.from(updated, null, msg));
        return ResponseEntity.ok(body);
    }

    private void revokeBestEffort(GmailAccount account, Long userId, String maskedEmail, String clientIp) {
        try {
            oauth2Service.revokeToken(account);
        } catch (Exception e) {
            log.warn("Lỗi khi revoke token Google (best-effort) cho {}: {}", maskedEmail, e.getMessage());
            auditLogService.log(userId, "REVOKE_TOKEN_FAILED",
                    "Revoke thất bại cho Gmail " + maskedEmail + ": " + e.getMessage(), clientIp);
        }
    }

    /**
     * Đổi tên hiển thị (displayName) của tài khoản Gmail liên kết.
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Đổi tên hiển thị của hộp thư Gmail")
    public ResponseEntity<GmailAccountResponse> updateDisplayName(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User currentUser) {

        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }

        String newName = body != null ? body.get("displayName") : null;
        if (newName == null || newName.isBlank() || newName.trim().length() > 100) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Tên hiển thị không được để trống và tối đa 100 ký tự.");
        }

        GmailAccount account = gmailAccountRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản Gmail với ID: " + id));

        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Bạn không có quyền thao tác trên tài khoản Gmail này.");
        }

        account.setDisplayName(newName.trim());
        GmailAccount saved = gmailAccountRepository.save(account);
        return ResponseEntity.ok(GmailAccountResponse.from(saved != null ? saved : account));
    }

    /**
     * Tải ảnh đại diện Google của tài khoản Gmail qua Server (chống IDOR & SSRF; Client không nối trực tiếp Google).
     * Trả 200 kèm ảnh nhị phân hoặc 204 No Content nếu tài khoản chưa có ảnh đại diện.
     */
    @GetMapping("/{id}/avatar")
    @Operation(summary = "Tải ảnh đại diện Google của hộp thư qua Server (chống IDOR/SSRF)")
    public ResponseEntity<byte[]> getGmailAccountAvatar(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }

        GmailAccount account = gmailAccountRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản Gmail với ID: " + id));

        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Bạn không có quyền truy cập tài khoản Gmail này.");
        }

        return oauth2Service.fetchAccountAvatar(account)
                .map(payload -> ResponseEntity.ok()
                        .header("Cache-Control", "private, max-age=3600")
                        .contentType(org.springframework.http.MediaType.parseMediaType(payload.contentType()))
                        .body(payload.bytes()))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Kiểm tra kết nối IMAP/SMTP thật cho tài khoản Gmail đã liên kết (hoạt động ở mọi profile, chỉ chủ sở hữu hoặc ADMIN).
     */
    @PostMapping("/{id}/test")
    @Operation(summary = "Kiểm tra kết nối IMAP/SMTP thật của hộp thư Gmail")
    public ResponseEntity<Map<String, Object>> testGmailAccountConnection(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }

        GmailAccount account = gmailAccountRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản Gmail với ID: " + id));

        if (!account.getUser().getId().equals(currentUser.getId()) && currentUser.getRole() != com.mailapp.model.Role.ADMIN) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Bạn không có quyền kiểm tra tài khoản Gmail này.");
        }

        long startTime = System.currentTimeMillis();
        boolean imapOk = false;
        int inboxCount = -1;
        boolean smtpOk = false;

        try (var imap = mailConnectionFactory.openImap(account)) {
            imapOk = imap.isConnected();
            try {
                jakarta.mail.Folder inbox = imap.folder("INBOX");
                if (inbox != null && inbox.exists()) {
                    inbox.open(jakarta.mail.Folder.READ_ONLY);
                    inboxCount = inbox.getMessageCount();
                    inbox.close(false);
                }
            } catch (Exception e) {
                log.warn("Kiểm tra IMAP mở inbox thất bại: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Kiểm tra IMAP thất bại: {}", e.getMessage());
        }

        try (var smtp = mailConnectionFactory.openSmtp(account)) {
            smtpOk = smtp.isConnected();
        } catch (Exception e) {
            log.warn("Kiểm tra SMTP thất bại: {}", e.getMessage());
        }

        if (imapOk) {
            account.setLastVerifiedAt(java.time.LocalDateTime.now());
            gmailAccountRepository.save(account);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(Map.of(
                "imapOk", imapOk,
                "inboxCount", inboxCount,
                "smtpOk", smtpOk,
                "activeMethod", account.getActiveMethod() != null ? account.getActiveMethod().name() : "",
                "fallbackActive", account.isFallbackActive(),
                "durationMs", durationMs
        ));
    }

    /**
     * [EP-G3] Hủy liên kết tài khoản Gmail.
     * Kiểm tra chặt chẽ quyền sở hữu (User A không thể xóa Gmail của User B).
     * Nếu có OAuth2 (kể cả khi preferredMethod là APP_PASSWORD), gửi yêu cầu thu hồi (revoke) tới Google;
     * lỗi revoke vẫn xóa local và ghi audit.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "[EP-G3] Hủy toàn bộ liên kết hộp thư Gmail")
    public ResponseEntity<Map<String, Object>> deleteGmailAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {

        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }

        GmailAccount account = gmailAccountRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản Gmail với ID: " + id));

        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Bạn không có quyền thao tác trên tài khoản Gmail này.");
        }

        String email = account.getEmail();
        String maskedEmail = MailCredentials.maskEmail(email);
        String clientIp = request.getRemoteAddr();

        if (account.hasOAuth() || account.getAuthType() == AuthType.OAUTH2) {
            revokeBestEffort(account, currentUser.getId(), maskedEmail, clientIp);
        }

        gmailAccountRepository.delete(account);
        auditLogService.log(currentUser.getId(), "UNLINK_GMAIL", "Đã hủy liên kết Gmail: " + email, clientIp);
        log.info("Người dùng id={} đã hủy liên kết Gmail id={}, email={}", currentUser.getId(), id, maskedEmail);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Đã hủy liên kết tài khoản Gmail " + email + " thành công."
        ));
    }
}
