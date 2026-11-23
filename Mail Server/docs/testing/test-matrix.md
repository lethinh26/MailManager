# Ma Trận Kiểm Thử Tự Động & Bằng Chứng Thực Nghiệm (Test Matrix)

Tài liệu ánh xạ toàn diện giữa yêu cầu kỹ thuật của đồ án Lập trình mạng PTIT (Server + Desktop Swing Client) với toàn bộ 48 bộ kiểm thử tự động (221 test case).

---

## 1. Bảng Ma Trận Ánh Xạ Yêu Cầu & Bộ Kiểm Thử

| STT | Yêu cầu kỹ thuật & Nghiệp vụ | Mã Module / EP | Test Suite Class | Loại Test | Kết quả | Bằng chứng thực nghiệm |
| :---: | :--- | :---: | :--- | :---: | :---: | :--- |
| **1** | Đăng nhập JWT (ký `HS256`), chống Brute-force 429 & Spoof X-Forwarded-For | **EP-A2** | `AuthControllerIntegrationTest`, `AuthServiceTest` | WebMvc / Unit | **PASSED (15)** | HTTP 200 JWT `{"alg":"HS256"}`; 6 lần sai -> 429 + `Retry-After`; chống Timing-Attack |
| **2** | Đăng ký (`USER` enforced), `/auth/me`, Đổi mật khẩu, `MustChangePasswordFilter`, `/public/config` | **EP-A1/A3/A4/P1** | `AuthRegisterAndChangePasswordTest` | WebMvcTest | **PASSED (9)** | 201 ép role `USER`; 403 `PASSWORD_CHANGE_REQUIRED` khi chưa đổi mật khẩu |
| **3** | Khởi tạo Admin an toàn (`ADMIN_INITIAL_PASSWORD` chỉ có mặc định ở `dev`) | **Bảo mật** | `AdminAccountInitializerTest`, `NonDevProfileTest`, `SecurityStartupValidatorTest` | Unit / Boot | **PASSED (9)** | Non-dev thiếu/yếu mật khẩu hoặc `KEYSTORE_PASSWORD` -> dừng khởi động ngay |
| **4** | Kiểm toán 401 toàn bộ API được bảo vệ | **Bảo mật** | `EndpointSecurityAuditTest` | SpringBootTest | **PASSED (1)** | Mọi `/api/**` ngoài permitAll đều trả 401 khi thiếu Bearer token |
| **5** | Mã hóa AES-256-GCM, JWT HS256, Filter, AuditLog, SystemSetting | **Bảo mật / Core** | `AesCipherTest`, `JwtProviderTest`, `JwtAuthenticationFilterTest`, `AuditLogServiceTest`, `SystemSettingServiceTest` | Unit Test | **PASSED (23)** | Xác nhận header JWT luôn là `HS256` kể cả khi khóa dài 64 bytes |
| **6** | Google OAuth2 PKCE + State + Callback kiểm tra Granular Consent Scope | **EP-O1 / EP-O2** | `OAuth2ControllerTest`, `OAuth2StateStoreTest`, `OAuth2ServiceTest`, `OAuth2MockWebServerTest` | Unit / MockWebServer | **PASSED (19)** | Thiếu scope `https://mail.google.com/` -> KHÔNG lưu DB, trả HTML lỗi tiếng Việt + audit `LINK_GMAIL_DENIED_SCOPE`; kiểm tra `access_denied`, sai `state` |
| **7** | Quản lý Gmail: List, EP-G2 App Password, PATCH tên hiển thị, Unlink + Anti-IDOR | **EP-G1/G2/G3/G4** | `GmailAccountControllerTest` | WebMvcTest | **PASSED (13)** | Chống IDOR 403/404; test IMAP trước khi lưu App Password; revoking token |
| **8** | MailConnectionFactory IMAPS/SMTP, XOAUTH2, Refresh Token, Retry & Che bí mật | **M07** | `DefaultMailConnectionFactoryGreenMailTest`, `XOAuth2IntegrationTest`, `AccessTokenProviderTest`, `MailConnectionFactoryTest`, `MailConnectionFaultAndRetryTest`, `GmailFolderResolverTest`, `SecretMaskingTest` | GreenMail / Unit | **PASSED (27)** | Tự refresh token <60s; `invalid_grant` -> `NEEDS_REAUTH`; lỗi mạng retry, lỗi auth fail ngay |
| **9** | Hộp thư thật: Folders, List/Search, Detail, Download Attachment, Send Multipart, Delete (Soft/Permanent), Flags | **M08/M10/M12/M16/M17** | `MailServiceGreenMailIntegrationTest` | GreenMail E2E | **PASSED (3)** | Đọc/tìm kiếm/tải đính kèm stream/gửi SMTP multipart/chuyển Thùng rác/đặt cờ `\Seen`/`\Flagged` |
| **10** | Làm sạch HTML (Jsoup Safelist), chặn ảnh từ xa, giữ `cid:`, làm sạch tên tệp & chống CRLF + Rate Limit gửi thư | **Bảo mật Mail** | `MailSecurityAndSanitizerTest` | Unit Test | **PASSED (2)** | Loại bỏ `<script>`, `<iframe>`, `onerror`, `../`, `\r\n`; chặn gửi quá 20 thư/giờ/user (`429`) |
| **11** | Kiểm thử Gmail thật (khi có `GMAIL_TEST_EMAIL` & `GMAIL_TEST_APP_PASSWORD`, chạy bằng `scripts/test-real-gmail.ps1`) | **M07 / M08** | `RealGmailIntegrationTest` | Integration | **SKIPPED (2)** | Tự động bỏ qua khi không thiết lập biến Gmail thật (`CHỜ NGƯỜI DÙNG`) |
| **12** | Client TLS 2 lớp (`.cer` hợp lệ vs sai), SessionStore, PasswordValidator, OAuth Flow | **Client Core** | `ApiClientTest`, `TlsSecurityTest`, `OAuthFlowAndSessionTest`, `PasswordValidatorTest`, `SessionStoreTest` | Unit / Local HTTP | **PASSED (22)** | Xác nhận đọc cả `"code"` & `"errorCode"`, `Retry-After`, từ chối chứng chỉ giả |
| **13** | Client API Hộp thư thật (folders, list, detail, `409 UID_VALIDITY_CHANGED`, `409 GMAIL_NEEDS_REAUTH`, SHA-256 download, send multipart, `429`, delete, flags, PATCH) | **Client Mail** | `MailApiClientNewEndpointsTest` | Local HTTP | **PASSED (6)** | Xác minh đầy đủ luồng mạng và xử lý mã lỗi mới của Server |
| **14** | Nghiệp vụ cốt lõi: Tải đính kèm rồi Xóa (hạnh phúc, lỗi tệp 2 KHÔNG xóa, trùng tên `(1)`, tên độc hại `../`, hủy giữa chừng KHÔNG xóa, dry-run, `NEEDS_REAUTH` dừng lô) | **Client Core Feature** | `AttachmentCleanupServiceTest` | Local HTTP + Disk | **PASSED (5)** | Khẳng định bất biến: tải lỗi hoặc hủy giữa chừng -> `deleteCalls == 0` |
| **15** | Avatar quyết định theo email, tương phản WCAG AA (>= 4.5:1), Logo Java2D & Sáng/Tối | **Client UI** | `AvatarThemeAndPrivacyTest` | Unit Test | **PASSED (3)** | Mọi màu bảng Avatar đạt tương phản >= 4.5:1; logo vẽ sắc nét 16..256px |
| **16** | Module Admin Server: RBAC 401/403, tạo/lọc/chi tiết user không lộ secret, chặn tự khóa/hạ quyền/xóa (400) & ADMIN cuối (409 `LAST_ADMIN`), khóa vô hiệu JWT tức thì, reset MK tạm 1 lần, xóa cascade + revoke OAuth2, validate settings (không tắt cả 2 loại liên kết), thống kê 7 ngày, nhật ký & chặn CSV Injection | **EP-AD1..AD8, EP-S1/S2, EP-ST1/ST2** | `AdminIntegrationTest` | SpringBootTest | **PASSED (8)** | Đầy đủ phân quyền, ràng buộc an toàn, cache tức thì & chống CSV Injection (`=`, `+`, `-`, `@`) |
| **17** | Module Admin Client (`AdminViewModel` & `ApiClient` 4 tab: Users, Settings, Stats, Logs + CSV + xử lý 403/401/409) | **M15 Admin UI** | `AdminModuleClientTest` | Unit / Local HTTP | **PASSED (4)** | Xác minh đầy đủ logic validate cấu hình, xác nhận xóa theo username, biểu đồ & xử lý mất quyền 403/401 |
| **18** | Kiểm thử E2E trực tiếp với Docker Server sống (`MAILAPP_E2E=1`) | **Live E2E** | `ClientLiveServerE2ETest` | Live HTTPS E2E | **PASSED (9)** *(hoặc 9 skipped khi offline)* | Xác thực cert thật vs cert giả, đăng ký, đăng nhập, 429 rate-limit, Admin đổi pass, OAuth2 URL, Anti-IDOR `/api/mails/folders` |
| **19** | Chuẩn hóa `email_key` dùng chung Server & Client (dấu chấm, `+tag`, `googlemail.com`, chữ hoa, tên miền Workspace) | **Multi-Auth / DB** | `EmailNormalizerTest`, `EmailNormalizerClientTest` | Unit Test | **PASSED (4)** | Khớp 100% giữa Server và Client; không bỏ dấu chấm/`+tag` trên tên miền Workspace |
| **20** | Flyway V3 migration dữ liệu cũ trùng `email_key` (giữ dòng cũ nhất, gộp bí mật OAuth2+AppPass, ghi cảnh báo `audit_logs`, thay `UNIQUE`) | **Flyway V3** | `FlywayV3MigrationTest` | H2 Flyway E2E | **PASSED (1)** | Nâng cấp V1→V2→V3 trên dữ liệu trùng; chặn chèn trùng `email_key` cùng user; cho phép 2 user dùng chung Gmail |
| **21** | Upsert 2 chiều (OAuth↔AppPass, lặp phương thức), race 2 request đồng thời, IDOR, đổi `preferredMethod`, gỡ phương thức & bảo vệ gỡ cái cuối (`confirmUnlink=true`) | **EP-G1..G3 + Mở rộng ngoài 27** | `GmailMultiAuthIntegrationTest` | SpringBootTest / MockMvc | **PASSED (5)** | Đúng `outcome` (`CREATED`/`METHOD_ADDED`/`METHOD_UPDATED`), giữ nguyên `id` & `displayName`, 2 thread race ra đúng 1 dòng, không lộ secret |
| **22** | Tự động fallback OAuth2 → App Password đúng 1 lần, giữ nguyên `NEEDS_REAUTH`, ghi audit `FALLBACK_TO_APP_PASSWORD` không lộ bí mật, 0 retry lỗi auth | **M07 Fallback** | `MailFallbackConnectionTest` | GreenMail / Unit | **PASSED (3)** | Lần 2 không gọi lại Google refresh; thiếu dự phòng giữ nguyên `ReauthRequiredException` (409); sai mật khẩu dự phòng retry = 0 |
| **23** | Client đa phương thức: pre-check cục bộ theo `email_key` trên Wizard App Password, xử lý `outcome`, gọi `PUT /preferred-method` & `DELETE /methods/{method}` | **Client Multi-Auth** | `GmailMultiAuthClientTest` | Unit / Local HTTP | **PASSED (3)** | Báo ngay trên form trước khi gọi mạng khi trùng hộp thư Google (kể cả biến thể dấu chấm/`+tag`/`googlemail.com`) |

---

## 2. Thống Kê Tổng Hợp Kết Quả Chạy Thực Tế

### 2.1. Mail Server (`./gradlew clean test --rerun-tasks`)
- **Tổng số test class:** 33
- **Tổng số test case:** **155** (**153 PASSED**, **2 SKIPPED** — `RealGmailIntegrationTest`, **0 FAILED**, **0 ERROR**)

### 2.2. Mail Client (`./gradlew clean test --rerun-tasks`)
- **Tổng số test class:** 15
- **Chế độ mặc định (không đặt `MAILAPP_E2E=1`):** **66** (**56 PASSED**, **10 SKIPPED** — `ClientLiveServerE2ETest`, **0 FAILED**)

### 2.3. Tổng Cộng Toàn Dự Án
- **48 test class — 221 test case** (chạy offline mặc định: **209 PASSED / 12 SKIPPED / 0 FAILED**).
