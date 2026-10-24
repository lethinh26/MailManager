# [BE] Khoá tài khoản user

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M13] Admin — Quản lý người dùng |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 30/10/2026 → 01/11/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Khoá tài khoản user` thuộc **[M13] Admin — Quản lý người dùng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-AD4 PUT /api/admin/users/{id}/lock`

- **Path:** `/api/admin/users/{id}/lock`
- **Method:** PUT
- **Role:** ADMIN | **Category:** Admin - User Management

**Mô tả:** Khoá tài khoản (enabled=false); chặn JWT cũ ở request sau; log ADMIN_LOCK_USER.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

PUT /api/admin/users/{id}/lock
- JWT + ADMIN; findById → 404
- Chặn: id == currentUser.id → 400 "Không thể tự khoá chính mình"
- Chặn: `user.role=ADMIN` && `userRepository.countByRoleAndEnabledTrue(ADMIN) <= 1` → 400 "Không thể khoá Admin cuối cùng"
- `enabled=false` → save; JWT cũ của user sẽ fail ở JwtFilter khi check `enabled` lần sau
- Log `ADMIN_LOCK_USER` `{targetUserId, username}`
- Response `{id, enabled:false}`

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M13] Admin — Quản lý người dùng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
