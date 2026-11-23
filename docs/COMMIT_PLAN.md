# Kế hoạch Đóng gói Commit (Commit Plan) — MailManager v0.1.0

Tài liệu này mô tả lịch sử commit sạch, phân nhóm theo từng tính năng / hạng mục thực sự đã hoàn thiện trên toàn hệ thống **MailManager** (`Mail Server` + `Mail Client`).

## Nguyên tắc thực hiện
- Sử dụng thời gian thật (không đặt `GIT_AUTHOR_DATE`/`GIT_COMMITTER_DATE`, không rebase đổi ngày, không sửa lịch sử cũ).
- Mỗi commit là một đơn vị thay đổi hoàn chỉnh theo tính năng, được kiểm chứng **compile & `./gradlew test` xanh trên đúng snapshot staged (`git write-tree`)** trước khi commit.
- Quét bí mật (`git diff --cached`) trước mỗi commit.
- **Tuyệt đối không commit**: `.env*` (bao gồm `.env` và `.env.example`), `*.p12`, `*.jks`, `*.cer`, `certs/`, `logs/`, `build/`, `bin/`, `.gradle/`, `.idea/` (trừ `.run/` chủ đích), `Mail Server/docs/evidence/` (chứa log phiên chạy thật chưa che hoàn toàn).

---

## Danh sách Commit theo thứ tự phụ thuộc (11 commit)

| # | Thông điệp (Conventional Commits) | Dự án kiểm thử | Phạm vi thay đổi chính |
|---|---|---|---|
| **1** | `chore(infra): add Docker setup, run configs, helper scripts and gitignore hardening` | Server + Client | `.gitignore`, `.gitattributes`, `.run/*`, `Mail Server/.gitignore`, `Mail Client/.gitignore`, `Mail Server/Dockerfile`, `Mail Server/.dockerignore`, `Mail Server/docker-compose.yml`, `Mail Server/entrypoint.sh`, `Mail Server/.run/*`, `Mail Client/.run/*`, `Mail Server/scripts/*`, `docs/COMMIT_PLAN.md` |
| **2** | `feat(auth): add Flyway schema, JWT/security filters, core services and auth endpoints` | Server | `Mail Server/build.gradle`, `application*.properties`, Flyway `V1__init.sql` & `V2__gmail_last_verified_at.sql`, `MailServerApplication`, `AdminAccountInitializer`, `DevController`, `PortInUseFailureAnalyzer`, `PublicHealthController`, `PublicConfigResponse`, `ErrorResponse`, `GlobalExceptionHandler`, `AuditLog`/`SystemSetting`/`GmailAccount` models & repos, `AesCipher`, `JwtProvider`, `JwtAuthenticationFilter`, `MustChangePasswordFilter`, `LoginRateLimiter`, `SecurityConfig`, `SecurityStartupValidator`, `AuditLogServiceImpl`, `SystemSettingServiceImpl` (xóa stub cũ), `AuthController`, `AuthService*`, `Register/ChangePassword/UserDto` và bộ test tương ứng |
| **3** | `feat(mail): optimize MailConnectionFactory IMAP fetchsize, timeouts and XOAuth2 support` | Server | `DefaultAccessTokenProvider`, `DefaultMailConnectionFactory`, `MailConnectionProperties`, `ConnectionTestResult`, cùng `AccessTokenProviderTest`, `MailConnectionFaultAndRetryTest`, `RealGmailIntegrationTest`, `XOAuth2IntegrationTest` |
| **4** | `feat(oauth2): implement PKCE OAuth2 callback, scope validation and Gmail account APIs` | Server | `GoogleOAuthConfig`, `PkceUtil`, `OAuth2StateStore`, `OAuth2Service*`, `GoogleTokenResponse`, `GoogleUserInfo`, `OAuth2Controller`, `GmailAccountController`, `GmailAccountResponse`, `LinkAppPasswordRequest`, cùng `OAuth2*Test`, `GmailAccountControllerTest`, `JwtAuthenticationFilterTest` |
| **5** | `feat(mail): implement IMAP/SMTP mailbox service, HTML sanitizer and rate guards` | Server | `MailController`, `MailDtos`, `FilenameSanitizer`, `HtmlSanitizerService`, `MailHeaderAndRateGuard`, `MailService`, `MailServiceImpl`, cùng `MailSecurityAndSanitizerTest`, `MailServiceGreenMailIntegrationTest`, `MailPerformanceAndResilienceTest` |
| **6** | `feat(admin): implement Admin user, settings, stats and audit log endpoints` | Server | `AdminController`, `AdminDtos`, `AdminService`, `AdminServiceImpl`, cùng `AdminIntegrationTest`, `EndpointSecurityAuditTest` |
| **7** | `feat(client): add TLS ApiClient, DTO models, session store, theme and safety utilities` | Client | `Mail Client/build.gradle`, `src/main/resources/icons/*`, `model/*`, `net/*`, `util/*`, `ui/theme/*`, cùng 6 file test nền tảng (`ApiClientTest`, `MailApiClientNewEndpointsTest`, `OAuthFlowAndSessionTest`, `PasswordValidatorTest`, `SessionStoreTest`, `TlsSecurityTest`) |
| **8** | `feat(client): implement attachment download-then-delete service and resilience tests` | Client | `service/AttachmentCleanupService.java`, cùng `AttachmentCleanupServiceTest`, `ClientSecurityAndResilienceTest` |
| **9** | `feat(ui): implement 4-tab AdminFrame with Java2D charts and Admin client tests` | Client | `ui/admin/*`, `ui/frames/AdminFrame`, cùng `AdminModuleClientTest` |
| **10** | `feat(ui): implement pure Swing auth, mailbox, compose, cleanup UI and Main launcher` | Client | `ui/frames/LoginFrame`, `RegisterFrame`, `MainFrame`, `ui/dialogs/*`, `Main.java`, cùng `AvatarThemeAndPrivacyTest`, `ClientLiveServerE2ETest` |
| **11** | `docs: update README, API contracts, diagrams, setup guides, mockups and release notes` | Docs (quét secret) | `README.md`, `RELEASE_NOTES.md`, `docs/COMMIT_PLAN.md`, `docs/INTELLIJ.md`, `docs/banner.png`, `docs/screenshots/mockup/*`, `Mail Server/docs/*` (trừ `docs/evidence/`) |

## Gắn thẻ phát hành (Release Tag)
Sau khi hoàn tất 11 commit, chạy lại `./gradlew clean test --rerun-tasks` cho cả `Mail Server` và `Mail Client`, quét bí mật toàn lịch sử (`git log -p` và `git grep`), và tạo tag `v0.1.0`.
