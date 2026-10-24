# [BE] Đăng nhập — trả JWT

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

Hoàn thành `[BE] Đăng nhập — trả JWT` thuộc **[M02] Xác thực tài khoản hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-A2 POST /api/auth/login`

- **Path:** `/api/auth/login`
- **Method:** POST
- **Role:** PUBLIC | **Category:** Authentication

**Mô tả:** Đăng nhập {username,password} -> JWT; kiểm tra enabled + MAINTENANCE_MODE; log LOGIN/LOGIN_FAILED.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

POST /api/auth/login
- Body: `{username, password}`
- Tìm user; sai → 401 `{code:"UNAUTHORIZED", message:"Sai tài khoản hoặc mật khẩu"}` + log `LOGIN_FAILED`
- `enabled=false` → 403 ACCOUNT_DISABLED
- `MAINTENANCE_MODE=true` và user.role != ADMIN → 403 với message bảo trì
- Đúng → `JwtProvider.generate()`, log `LOGIN`
- Response 200: `{accessToken, tokenType:"Bearer", expiresIn:86400, user:{id, username, fullName, email, role, mustChangePassword}}`
- Test: login OK → dùng token gọi /api/auth/me; user bị khoá → 403; maintenance mode → 403 với USER, OK với ADMIN

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
