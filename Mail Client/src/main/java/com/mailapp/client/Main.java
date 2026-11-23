package com.mailapp.client;

import com.mailapp.client.model.AdminClientDtos.*;
import com.mailapp.client.model.GmailAccountDto;
import com.mailapp.client.model.MailClientDtos.*;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.model.UserDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.service.AttachmentCleanupService;
import com.mailapp.client.ui.dialogs.*;
import com.mailapp.client.ui.frames.AdminFrame;
import com.mailapp.client.ui.frames.LoginFrame;
import com.mailapp.client.ui.frames.MainFrame;
import com.mailapp.client.ui.frames.RegisterFrame;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.util.ScreenshotUtil;
import com.mailapp.client.util.SessionStore;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if ("-Dmailclient.insecure=true".equalsIgnoreCase(arg)
                        || "--insecure".equalsIgnoreCase(arg)
                        || "-insecure".equalsIgnoreCase(arg)
                        || "insecure=true".equalsIgnoreCase(arg)) {
                    System.setProperty("mailclient.insecure", "true");
                    System.out.println("[MAILCLIENT] Chế độ bỏ qua xác thực TLS đã được bật (insecure=true)");
                }
            }
        }
        List<String> argList = args != null ? Arrays.asList(args) : List.of();
        boolean generateIconsMode = argList.contains("--generate-icons");
        boolean screenshotMode = argList.contains("--screenshots");
        boolean hidpiScreenshotMode = argList.contains("--screenshots-hidpi");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        if (generateIconsMode) {
            generateBrandAssets();
            System.exit(0);
            return;
        }

        if (hidpiScreenshotMode) {
            generateHiDpiAndMinSizeScreenshots();
            System.out.println("=== ĐÃ XUẤT ẢNH KIỂM TRA HIDPI & 800x600 THÀNH CÔNG ===");
            System.exit(0);
        } else if (screenshotMode) {
            generateScreenshots();
            System.out.println("=== TOÀN BỘ ẢNH SCREENSHOT MOCKUP ĐÃ ĐƯỢC SINH THÀNH CÔNG ===");
            System.exit(0);
        } else {
            SwingUtilities.invokeLater(() -> {
                SplashWindow splash = new SplashWindow();
                splash.showBriefly(() -> {
                    try {
                        ApiClient apiClient = new ApiClient();
                        LoginFrame loginFrame = new LoginFrame(apiClient);
                        loginFrame.setVisible(true);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(null,
                                ex.getMessage(),
                                "Lỗi Khởi Động Client",
                                JOptionPane.ERROR_MESSAGE);
                    }
                });
            });
        }
    }

    public static void generateBrandAssets() {
        try {
            File iconsDir = new File("src/main/resources/icons");
            File bannerFile = new File("../docs/banner.png");
            if (!new File("src/main/java").exists()) {
                iconsDir = new File("Mail Client/src/main/resources/icons");
                bannerFile = new File("docs/banner.png");
            }
            AppIcons.generateBrandAssets(iconsDir, bannerFile);
            System.out.println("Đã xuất bộ logo PNG/ICO vào: " + iconsDir.getAbsolutePath());
            System.out.println("Đã xuất banner README vào: " + bannerFile.getAbsolutePath());
        } catch (Exception ex) {
            throw new RuntimeException("Lỗi xuất tài nguyên thương hiệu: " + ex.getMessage(), ex);
        }
    }

    public static void generateScreenshots() {
        File targetDir = new File("docs/screenshots/mockup");
        if (!targetDir.exists() && new File("../docs").exists()) {
            targetDir = new File("../docs/screenshots/mockup");
        }
        targetDir.mkdirs();
        String outputDir = targetDir.getAbsolutePath();
        System.out.println("Bắt đầu sinh screenshots vào: " + outputDir);

        ApiClient dummyClient;
        try {
            dummyClient = new ApiClient();
        } catch (Exception e) {
            System.setProperty("mailclient.insecure", "true");
            dummyClient = new ApiClient("https://localhost:8443");
        }

        boolean originalDark = Theme.isDark();
        Theme.setDark(false);

        // 1. Login thường (split-screen 940x650)
        LoginFrame normalLogin = new LoginFrame(dummyClient, false);
        normalLogin.applyPublicConfig(new PublicConfigDto(true, true, true, false, true, 5, 25));
        normalLogin.setPrefilledUsername("cuongdfle");
        normalLogin.setSize(940, 650);
        ScreenshotUtil.captureComponent(normalLogin, outputDir, "01-login-normal.png", true);
        normalLogin.dispose();

        // 2. Login chế độ bảo trì (split-screen 940x650)
        LoginFrame maintLogin = new LoginFrame(dummyClient, false);
        maintLogin.applyPublicConfig(new PublicConfigDto(false, false, false, true, false, 5, 25));
        maintLogin.setPrefilledUsername("admin");
        maintLogin.setSize(940, 650);
        ScreenshotUtil.captureComponent(maintLogin, outputDir, "02-login-maintenance.png", true);
        maintLogin.dispose();

        // 3. Register
        RegisterFrame registerFrame = new RegisterFrame(dummyClient, null);
        registerFrame.setFormFields("nguyen_van_a", "Nguyễn Văn A", "nguyenvana@ptit.edu.vn", "SecurePass2026!", "SecurePass2026!");
        registerFrame.setSize(580, 680);
        ScreenshotUtil.captureComponent(registerFrame, outputDir, "03-register.png", true);
        registerFrame.dispose();

        // 4. Forced Change Password
        JFrame dummyParent = new JFrame();
        ChangePasswordDialog forcedDialog = new ChangePasswordDialog(dummyParent, dummyClient, true);
        forcedDialog.setFormFields("TempOldPass1!", "Admin@Secure2026!", "Admin@Secure2026!");
        forcedDialog.setSize(480, 430);
        ScreenshotUtil.captureComponent(forcedDialog, outputDir, "04-change-password-forced.png", true);
        forcedDialog.dispose();

        // Dữ liệu mô phỏng (chỉ tồn tại trong --screenshots)
        UserDto adminUser = new UserDto(1L, "cuongdfle", "Lê Thanh Cường", "cuongdfle@gmail.com", "ADMIN", false, "2026-11-22T08:00:00Z");
        SessionStore.getInstance().updateUser(adminUser);
        List<GmailAccountDto> sampleAccounts = List.of(
                new GmailAccountDto(1L, "cuongdfle@gmail.com", "Lê Thanh Cường (Chính)", "OAUTH2", "ACTIVE", null, "2026-11-15T10:00:00Z", "2026-11-22T08:00:00Z", "2026-11-22T08:05:00Z"),
                new GmailAccountDto(2L, "cuong.work@ptit.edu.vn", "PTIT Work Mail", "APP_PASSWORD", "NEEDS_REAUTH", "2026-11-20T12:00:00Z", "2026-11-16T10:00:00Z", "2026-11-22T08:00:00Z", "2026-11-20T08:00:00Z")
        );
        List<MailFolderDto> sampleFolders = List.of(
                new MailFolderDto("INBOX", "INBOX", "Hộp thư đến", "INBOX", 3, 128),
                new MailFolderDto("STARRED", "[Gmail]/Starred", "Có gắn dấu sao", "STARRED", 0, 14),
                new MailFolderDto("SENT", "[Gmail]/Sent Mail", "Đã gửi", "SENT", 0, 56),
                new MailFolderDto("DRAFTS", "[Gmail]/Drafts", "Thư nháp", "DRAFTS", 1, 2),
                new MailFolderDto("SPAM", "[Gmail]/Spam", "Thư rác", "SPAM", 0, 5),
                new MailFolderDto("TRASH", "[Gmail]/Trash", "Thùng rác", "TRASH", 0, 9)
        );
        List<MailSummaryDto> sampleMails = List.of(
                new MailSummaryDto(101L, 1L, new MailAddressDto("Phòng Đào Tạo PTIT", "daotao@ptit.edu.vn"),
                        List.of(new MailAddressDto("Lê Thanh Cường", "cuongdfle@gmail.com")),
                        "Thông báo lịch bảo vệ Đồ án Lập trình Mạng — Học kỳ 1",
                        "Đính kèm danh sách hội đồng và quy định nộp báo cáo bản cuối...",
                        "2026-11-22T09:15:00Z", new MailFlagsDto(false, true, false, false), 542_300L, true, 2),
                new MailSummaryDto(102L, 1L, new MailAddressDto("GitHub Notifications", "notifications@github.com"),
                        List.of(new MailAddressDto("Lê Thanh Cường", "cuongdfle@gmail.com")),
                        "[MailManager] Pull Request #42: Hoàn thiện API hộp thư thật",
                        "Tất cả 123 kiểm thử phía Server và GreenMail đã vượt qua...",
                        "2026-11-22T08:30:00Z", new MailFlagsDto(false, false, false, false), 18_420L, false, 0),
                new MailSummaryDto(103L, 1L, new MailAddressDto("Google Security", "no-reply@accounts.google.com"),
                        List.of(new MailAddressDto("Lê Thanh Cường", "cuongdfle@gmail.com")),
                        "Cảnh báo bảo mật: Đã cấp quyền OAuth2 cho network_programming_mail_project",
                        "Ứng dụng vừa được liên kết bằng giao thức OAuth2 PKCE an toàn.",
                        "2026-11-21T16:45:00Z", new MailFlagsDto(true, false, true, false), 12_100L, false, 0)
        );
        MailDetailResponse sampleDetail = new MailDetailResponse(
                101L, 1L,
                new MailAddressDto("Phòng Đào Tạo PTIT", "daotao@ptit.edu.vn"),
                List.of(new MailAddressDto("Lê Thanh Cường", "cuongdfle@gmail.com")),
                List.of(new MailAddressDto("Giảng viên hướng dẫn", "gv@ptit.edu.vn")),
                List.of(), List.of(),
                "2026-11-22T09:15:00Z", "<ptit-2026-101@ptit.edu.vn>", null, null,
                "Thông báo lịch bảo vệ Đồ án Lập trình Mạng — Học kỳ 1",
                "Chào bạn, đính kèm là lịch bảo vệ đồ án môn Lập trình Mạng và mẫu biên bản nghiệm thu.",
                "<h3>Thông báo lịch bảo vệ Đồ án</h3><p>Chào sinh viên <b>Lê Thanh Cường</b>,</p>"
                        + "<p>Vui lòng tải xuống 2 tệp đính kèm bên dưới và kiểm tra kỹ danh sách hội đồng bảo vệ.</p>"
                        + "<ul><li>Thời gian: 08:00 ngày 28/11/2026</li><li>Phòng: A2-401 (Cơ sở Hà Đông)</li></ul>",
                false, true,
                List.of(
                        new MailAttachmentDto(0, "Lich_Bao_Ve_LTM_2026.pdf", "application/pdf", 384_120L, false, null),
                        new MailAttachmentDto(1, "Mau_Bien_Ban_Nghiem_Thu.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 158_180L, false, null)
                ),
                new MailFlagsDto(true, true, false, false)
        );

        // 5. MainFrame 3 cột Light Mode (có NEEDS_REAUTH + đính kèm + banner chặn ảnh từ xa)
        MainFrame mainLight = new MainFrame(dummyClient, false);
        mainLight.populateMockupForScreenshots(adminUser, sampleAccounts, sampleFolders, sampleMails, sampleDetail);
        mainLight.setSize(1280, 780);
        ScreenshotUtil.captureComponent(mainLight, outputDir, "05-main-frame-needs-reauth.png", true);
        mainLight.dispose();

        // 6. MainFrame 3 cột Dark Mode
        Theme.setDark(true);
        MainFrame mainDark = new MainFrame(dummyClient, false);
        mainDark.populateMockupForScreenshots(adminUser, sampleAccounts, sampleFolders, sampleMails, sampleDetail);
        mainDark.setSize(1280, 780);
        ScreenshotUtil.captureComponent(mainDark, outputDir, "06-main-frame-dark.png", true);
        mainDark.dispose();

        // 7. ComposeDialog (Light)
        Theme.setDark(false);
        ComposeDialog compose = new ComposeDialog(dummyParent, dummyClient, sampleAccounts, sampleAccounts.get(0),
                new PublicConfigDto(true, true, true, false, true, 5, 25), null);
        compose.prefillReplyOrForward(sampleDetail, "REPLY");
        compose.setSize(760, 620);
        ScreenshotUtil.captureComponent(compose, outputDir, "07-compose-dialog.png", true);
        compose.dispose();

        // 8. CleanupPreviewDialog (Tải đính kèm & Xóa)
        CleanupPreviewDialog cleanup = new CleanupPreviewDialog(dummyParent, new AttachmentCleanupService(dummyClient),
                1L, "cuongdfle@gmail.com", "INBOX", List.of(sampleMails.get(0)), null);
        cleanup.setSize(640, 480);
        ScreenshotUtil.captureComponent(cleanup, outputDir, "08-cleanup-preview.png", true);
        cleanup.dispose();

        // 9..12. AdminFrame 4 tab (Người dùng, Cấu hình, Thống kê, Nhật ký) — có watermark MÔ PHỎNG
        AdminPageResponse<AdminUserSummary> sampleUsersPage = new AdminPageResponse<>(List.of(
                new AdminUserSummary(1L, "admin", "Quản Trị Viên", "admin@ptit.edu.vn", "ADMIN", true, false, 1L, "2026-11-01T08:00:00", "2026-11-22T08:00:00"),
                new AdminUserSummary(2L, "cuongdfle", "Lê Thanh Cường", "cuongdfle@gmail.com", "USER", true, false, 2L, "2026-11-10T09:15:00", "2026-11-22T09:00:00"),
                new AdminUserSummary(3L, "locked_user", "Tài Khoản Bị Khóa", "locked@ptit.edu.vn", "USER", false, true, 0L, "2026-11-15T14:20:00", "2026-11-20T11:30:00")
        ), 0, 20, 3, 1);
        List<AdminSettingItem> sampleSettings = List.of(
                new AdminSettingItem("ALLOW_REGISTRATION", "true", "BOOLEAN", null, null, "Cho phép tự đăng ký", "admin", "2026-11-22T09:00:00"),
                new AdminSettingItem("ALLOW_OAUTH2_LINK", "true", "BOOLEAN", null, null, "Cho phép OAuth2", "admin", "2026-11-22T09:00:00"),
                new AdminSettingItem("ALLOW_APP_PASSWORD_LINK", "true", "BOOLEAN", null, null, "Cho phép App Password", "admin", "2026-11-22T09:00:00"),
                new AdminSettingItem("ALLOW_SEND_MAIL", "true", "BOOLEAN", null, null, "Cho phép gửi thư", "admin", "2026-11-22T09:00:00"),
                new AdminSettingItem("MAINTENANCE_MODE", "false", "BOOLEAN", null, null, "Chế độ bảo trì", "admin", "2026-11-22T09:00:00"),
                new AdminSettingItem("MAX_GMAIL_PER_USER", "5", "INTEGER", 0, 50, "Số Gmail tối đa", "admin", "2026-11-22T09:00:00"),
                new AdminSettingItem("MAX_ATTACHMENT_SIZE_MB", "25", "INTEGER", 1, 50, "Dung lượng đính kèm", "admin", "2026-11-22T09:00:00"),
                new AdminSettingItem("MAIL_PAGE_SIZE", "30", "INTEGER", 10, 100, "Số thư mỗi trang", "admin", "2026-11-22T09:00:00"),
                new AdminSettingItem("LOGIN_MAX_FAILED_ATTEMPTS", "5", "INTEGER", 3, 20, "Giới hạn đăng nhập sai", "admin", "2026-11-22T09:00:00")
        );
        AdminStatsResponse sampleStats = new AdminStatsResponse(
                18, 16, 2, 2, 14, 9, 5, 1, 2, 6, 18, 4,
                List.of(
                        new DailyCountPoint("2026-11-16", 3),
                        new DailyCountPoint("2026-11-17", 7),
                        new DailyCountPoint("2026-11-18", 5),
                        new DailyCountPoint("2026-11-19", 11),
                        new DailyCountPoint("2026-11-20", 8),
                        new DailyCountPoint("2026-11-21", 14),
                        new DailyCountPoint("2026-11-22", 9)
                )
        );
        AdminPageResponse<AdminAuditLogItem> sampleLogsPage = new AdminPageResponse<>(List.of(
                new AdminAuditLogItem(101L, 1L, "admin", "ADMIN_CHANGE_SETTING", "Admin [admin] đổi cấu hình MAX_GMAIL_PER_USER: 5 -> 8", "127.0.0.1", "2026-11-22T18:01:00"),
                new AdminAuditLogItem(100L, 1L, "admin", "ADMIN_LOCK_USER", "Admin [admin] đã khóa tài khoản [locked_user] (id=3)", "127.0.0.1", "2026-11-22T17:55:00"),
                new AdminAuditLogItem(99L, 2L, "cuongdfle", "SEND_MAIL", "Gửi thư thành công (gmailId=1, to=1, attachments=1)", "192.168.1.15", "2026-11-22T17:40:00")
        ), 0, 25, 3, 1);

        AdminFrame adminMock = new AdminFrame(dummyClient, null, false);
        adminMock.populateMockupForScreenshots(sampleUsersPage, sampleSettings, sampleStats, sampleLogsPage);
        adminMock.setSize(1160, 740);
        adminMock.selectTab(0);
        ScreenshotUtil.captureComponent(adminMock, outputDir, "09-admin-users.png", true);
        adminMock.selectTab(1);
        ScreenshotUtil.captureComponent(adminMock, outputDir, "10-admin-settings.png", true);
        adminMock.selectTab(2);
        ScreenshotUtil.captureComponent(adminMock, outputDir, "11-admin-stats.png", true);
        adminMock.selectTab(3);
        ScreenshotUtil.captureComponent(adminMock, outputDir, "12-admin-logs.png", true);
        adminMock.dispose();

        dummyParent.dispose();
        Theme.setDark(originalDark);
    }

    /**
     * Xuất bộ ảnh kiểm tra HiDPI (1.0, 1.25, 1.5, 2.0) ở kích thước tối thiểu 800x600 với chủ đề Sáng & Tối.
     */
    public static void generateHiDpiAndMinSizeScreenshots() {
        File targetDir = new File("docs/screenshots/mockup");
        if (!targetDir.exists() && new File("../docs").exists()) {
            targetDir = new File("../docs/screenshots/mockup");
        }
        targetDir.mkdirs();
        String outputDir = targetDir.getAbsolutePath();
        String scaleTag = System.getProperty("sun.java2d.uiScale", "1.0").replace('.', '_');

        ApiClient dummyClient = new ApiClient("http://localhost:19999");
        boolean originalDark = Theme.isDark();

        UserDto demoUser = new UserDto(1L, "demo_admin", "Nguyễn Văn An", "demo@example.com", "ADMIN", false, "2026-11-22T08:00:00Z");
        SessionStore.getInstance().updateUser(demoUser);
        List<GmailAccountDto> accounts = List.of(
                new GmailAccountDto(1L, "demo@example.com", "Tài khoản chính", "OAUTH2", "ACTIVE", null, "2026-11-15T10:00:00Z", "2026-11-22T08:00:00Z", "2026-11-22T08:05:00Z")
        );
        List<MailFolderDto> folders = List.of(
                new MailFolderDto("INBOX", "INBOX", "Hộp thư đến", "INBOX", 1, 12),
                new MailFolderDto("SENT", "[Gmail]/Sent Mail", "Đã gửi", "SENT", 0, 4),
                new MailFolderDto("TRASH", "[Gmail]/Trash", "Thùng rác", "TRASH", 0, 2)
        );
        List<MailSummaryDto> mails = List.of(
                new MailSummaryDto(101L, 1L, new MailAddressDto("Phòng Đào Tạo", "daotao@ptit.edu.vn"),
                        List.of(new MailAddressDto("Nguyễn Văn An", "demo@example.com")),
                        "Thông báo kiểm tra giao diện ở độ phân giải tối thiểu 800x600",
                        "Kiểm tra cắt chữ, chồng lấn, tương phản WCAG AA...",
                        "2026-11-22T09:15:00Z", new MailFlagsDto(false, true, false, false), 124_000L, true, 1)
        );
        MailDetailResponse detail = new MailDetailResponse(
                101L, 1L,
                new MailAddressDto("Phòng Đào Tạo", "daotao@ptit.edu.vn"),
                List.of(new MailAddressDto("Nguyễn Văn An", "demo@example.com")),
                List.of(), List.of(), List.of(),
                "2026-11-22T09:15:00Z", "<msg-101@example.com>", null, null,
                "Thông báo kiểm tra giao diện 800x600",
                "Nội dung mẫu kiểm tra hiển thị.",
                "<p>Nội dung mẫu kiểm tra <b>HiDPI " + System.getProperty("sun.java2d.uiScale", "1.0") + "x</b> ở cửa sổ 800x600.</p>",
                false, true,
                List.of(new MailAttachmentDto(0, "bao-cao.pdf", "application/pdf", 124_000L, false, null)),
                new MailFlagsDto(true, true, false, false)
        );

        // 1. MainFrame 800x600 Light
        Theme.setDark(false);
        MainFrame mainLight = new MainFrame(dummyClient, false);
        mainLight.populateMockupForScreenshots(demoUser, accounts, folders, mails, detail);
        mainLight.setSize(800, 600);
        ScreenshotUtil.captureComponent(mainLight, outputDir, "hidpi-" + scaleTag + "-main-800x600-light.png", true);
        mainLight.dispose();

        // 2. MainFrame 800x600 Dark
        Theme.setDark(true);
        MainFrame mainDark = new MainFrame(dummyClient, false);
        mainDark.populateMockupForScreenshots(demoUser, accounts, folders, mails, detail);
        mainDark.setSize(800, 600);
        ScreenshotUtil.captureComponent(mainDark, outputDir, "hidpi-" + scaleTag + "-main-800x600-dark.png", true);
        mainDark.dispose();

        // 3. LoginFrame Dark (split-screen 800x600, chứng minh hỗ trợ Dark Mode & WCAG AA)
        LoginFrame loginDark = new LoginFrame(dummyClient, false);
        loginDark.applyPublicConfig(new PublicConfigDto(true, true, true, false, true, 5, 25));
        loginDark.setPrefilledUsername("demo_admin");
        loginDark.setSize(800, 600);
        ScreenshotUtil.captureComponent(loginDark, outputDir, "hidpi-" + scaleTag + "-login-dark.png", true);
        loginDark.dispose();

        // 4. AdminFrame 800x600 Light
        Theme.setDark(false);
        AdminFrame admin800 = new AdminFrame(dummyClient, null, false);
        admin800.populateMockupForScreenshots(
                new AdminPageResponse<>(List.of(
                        new AdminUserSummary(1L, "admin", "Quản Trị Viên", "admin@example.com", "ADMIN", true, false, 1L, "2026-11-01T08:00:00", "2026-11-22T08:00:00")
                ), 0, 20, 1, 1),
                List.of(new AdminSettingItem("ALLOW_REGISTRATION", "true", "BOOLEAN", null, null, "Cho phép tự đăng ký", "admin", "2026-11-22T09:00:00")),
                new AdminStatsResponse(10, 9, 1, 1, 8, 5, 3, 0, 1, 3, 9, 2, List.of(new DailyCountPoint("2026-11-22", 5))),
                new AdminPageResponse<>(List.of(new AdminAuditLogItem(1L, 1L, "admin", "LOGIN", "Đăng nhập thành công", "127.0.0.1", "2026-11-22T09:00:00")), 0, 25, 1, 1)
        );
        admin800.setSize(800, 600);
        admin800.selectTab(0);
        ScreenshotUtil.captureComponent(admin800, outputDir, "hidpi-" + scaleTag + "-admin-800x600-light.png", true);
        admin800.dispose();

        Theme.setDark(originalDark);
    }
}
