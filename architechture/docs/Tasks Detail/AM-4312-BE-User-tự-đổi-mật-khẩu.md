# [BE] User tự đổi mật khẩu

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M02] Xác thực tài khoản hệ thống |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 29/10/2026 → 31/10/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] User tự đổi mật khẩu` thuộc **[M02] Xác thực tài khoản hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-A4 POST /api/auth/change-password`

- **Path:** `/api/auth/change-password`
- **Method:** POST
- **Role:** AUTHENTICATED | **Category:** Authentication

**Mô tả:** User tự đổi mật khẩu {currentPassword,newPassword}; xoá cờ must_change_password.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

POST /api/auth/change-password
- JWT bắt buộc; user hiện tại từ token
- Body `{currentPassword, newPassword}`
- Verify `BCrypt.matches(currentPassword, user.password)` → sai: 400 `VALIDATION_ERROR` message "Mật khẩu hiện tại sai"
- `newPassword` ≥8 ký tự, không trùng currentPassword → 400 VALIDATION_ERROR
- Update hash + `must_change_password=false`
- Log `CHANGE_PASSWORD`
- Response 200 `{message:"Đổi mật khẩu thành công"}`

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M02] Xác thực tài khoản hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
