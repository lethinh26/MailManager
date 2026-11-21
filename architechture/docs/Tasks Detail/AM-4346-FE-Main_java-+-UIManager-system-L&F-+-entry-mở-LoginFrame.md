# [FE] Main.java + UIManager system L&F + entry mở LoginFrame

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M03] Client nền tảng & Màn hình vào hệ thống |
| Owner | TV1 |
| Platform | UserWeb |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 29/10/2026 → 29/10/2026 |
| Ước lượng | 0.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Main.java + UIManager system L&F + entry mở LoginFrame` thuộc **[M03] Client nền tảng & Màn hình vào hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `LoginFrame extends JFrame`: form username + `JPasswordField`, nút "Đăng nhập", link "Chưa có tài khoản? Đăng ký" (ẩn nếu `publicConfig.registrationEnabled=false`), link "Quên mật khẩu" (placeholder)
- Bấm Đăng nhập → `SwingWorker` gọi `ApiClient.post("/api/auth/login")` → success: lưu token vào SessionStore, gọi `/api/auth/me`, nếu `mustChangePassword` → `ChangePasswordDialog`, else → `MainFrame`
- Error: `ApiException` → `JOptionPane` hiện `message`; ACCOUNT_DISABLED → text riêng "Tài khoản bị khoá"
- Disable nút Đăng nhập trong lúc gọi API + show "Đang đăng nhập..."
- Bảo trì: nếu `publicConfig.maintenanceMode=true` → hiện banner "Hệ thống đang bảo trì"

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M03] Client nền tảng & Màn hình vào hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
