# [BE] Đăng ký tài khoản mới

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M02] Xác thực tài khoản hệ thống |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 28/10/2026 → 30/10/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Đăng ký tài khoản mới` thuộc **[M02] Xác thực tài khoản hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-A1 POST /api/auth/register`

- **Path:** `/api/auth/register`
- **Method:** POST
- **Role:** PUBLIC | **Category:** Authentication

**Mô tả:** Đăng ký tài khoản mới {username,password,fullName,email}; kiểm tra cờ ALLOW_REGISTRATION; role=USER; BCrypt; log REGISTER.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

POST /api/auth/register
- Body: `{username, password, fullName, email}`
- Validation: username 3-64 ký tự không trùng (case-insensitive), password ≥8, email hợp lệ
- Kiểm tra `SystemSettingService.getBool("ALLOW_REGISTRATION")` trước → nếu false: 403 `{code:"REGISTRATION_DISABLED"}`
- Hash password bằng BCrypt (strength 10)
- role=USER, enabled=true, must_change_password=false
- Ghi log `REGISTER`
- Response 201: `{id, username, fullName, email, role, createdAt}`
- Errors: 400 VALIDATION_ERROR (thiếu field / email sai / password yếu), 403 REGISTRATION_DISABLED, 409 username trùng
- Test: Postman → 201; gọi lại username cũ → 409; tắt flag → 403

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
