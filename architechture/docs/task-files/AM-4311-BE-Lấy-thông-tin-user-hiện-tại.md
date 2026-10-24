# [BE] Lấy thông tin user hiện tại

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
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Lấy thông tin user hiện tại` thuộc **[M02] Xác thực tài khoản hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-A3 GET /api/auth/me`

- **Path:** `/api/auth/me`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** Authentication

**Mô tả:** Thông tin user hiện tại từ JWT (role, mustChangePassword) — Client gọi sau login để hiện khu Admin.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/auth/me
- JWT bắt buộc (JwtFilter)
- Trả `{id, username, fullName, email, role, mustChangePassword, createdAt}`
- KHÔNG trả password
- Client gọi sau login để: hiện tên user, biết role → hiện/ẩn khu Admin, biết mustChangePassword → bắt đổi
- Test: GET với Bearer → 200; không Bearer → 401; Bearer hết hạn → 401

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
