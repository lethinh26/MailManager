package com.mailapp.config;

import com.mailapp.exception.AppException;
import com.mailapp.mail.ImapConnection;
import com.mailapp.mail.MailConnectionFactory;
import com.mailapp.mail.SmtpConnection;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.service.SystemSettingService;
import jakarta.mail.Folder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller tiện ích chỉ chạy ở môi trường DEV (profile dev) và BẮT BUỘC đăng nhập quyền ADMIN.
 * Cho phép giả lập bật/tắt chế độ bảo trì và các cờ cấu hình để kiểm thử trực tiếp bằng HTTP client,
 * cùng endpoint selftest kết nối IMAP/SMTP của Gmail account.
 *
 * @author Lê Thanh Cường
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
@PreAuthorize("hasRole('ADMIN')")
public class DevController {

    private static final Logger log = LoggerFactory.getLogger(DevController.class);

    private final SystemSettingService systemSettingService;
    private final GmailAccountRepository gmailAccountRepository;
    private final MailConnectionFactory mailConnectionFactory;

    public DevController(SystemSettingService systemSettingService,
                         GmailAccountRepository gmailAccountRepository,
                         MailConnectionFactory mailConnectionFactory) {
        this.systemSettingService = systemSettingService;
        this.gmailAccountRepository = gmailAccountRepository;
        this.mailConnectionFactory = mailConnectionFactory;
    }

    @PostMapping("/maintenance-mode")
    public ResponseEntity<Map<String, Object>> setMaintenanceMode(@RequestParam boolean enabled) {
        systemSettingService.setMaintenanceMode(enabled);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "maintenanceMode", enabled,
                "message", enabled ? "Đã bật chế độ bảo trì (chỉ ADMIN đăng nhập được)" : "Đã tắt chế độ bảo trì"
        ));
    }

    @PostMapping("/setting")
    public ResponseEntity<Map<String, Object>> updateSetting(@RequestParam String key, @RequestParam String value) {
        systemSettingService.updateSetting(key, value);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "key", key,
                "value", value,
                "message", "Đã cập nhật cấu hình " + key + "=" + value
        ));
    }

    /**
     * [EP-D3] Endpoint selftest kết nối IMAP và SMTP qua OAuth2/App Password cho tài khoản Gmail chỉ trong DEV.
     * Kiểm tra quyền sở hữu, trả về imapOk, inboxCount, smtpOk, durationMs (tuyệt đối không lộ token).
     */
    @PostMapping("/oauth2-selftest/{id}")
    public ResponseEntity<Map<String, Object>> oauth2Selftest(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }

        GmailAccount account = gmailAccountRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản Gmail với ID: " + id));

        // Kiểm tra quyền sở hữu: Phải là tài khoản của chính user hoặc user có quyền ADMIN
        if (!account.getUser().getId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Bạn không có quyền kiểm tra tài khoản Gmail này.");
        }

        long startTime = System.currentTimeMillis();
        boolean imapOk = false;
        int inboxCount = -1;
        boolean smtpOk = false;

        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            imapOk = imap.isConnected();
            try {
                Folder inbox = imap.folder("INBOX");
                if (inbox != null && inbox.exists()) {
                    inbox.open(Folder.READ_ONLY);
                    inboxCount = inbox.getMessageCount();
                    inbox.close(false);
                }
            } catch (Exception e) {
                log.warn("Selftest IMAP mở inbox thất bại: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Selftest IMAP thất bại: {}", e.getMessage());
        }

        try (SmtpConnection smtp = mailConnectionFactory.openSmtp(account)) {
            smtpOk = smtp.isConnected();
        } catch (Exception e) {
            log.warn("Selftest SMTP thất bại: {}", e.getMessage());
        }

        long durationMs = System.currentTimeMillis() - startTime;

        return ResponseEntity.ok(Map.of(
                "imapOk", imapOk,
                "inboxCount", inboxCount,
                "smtpOk", smtpOk,
                "durationMs", durationMs
        ));
    }
}
