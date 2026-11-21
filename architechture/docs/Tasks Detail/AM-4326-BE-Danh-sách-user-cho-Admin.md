# [BE] Danh sách user cho Admin

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M13] Admin — Quản lý người dùng |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 29/10/2026 → 31/10/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Danh sách user cho Admin` thuộc **[M13] Admin — Quản lý người dùng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-AD2 GET /api/admin/users`

- **Path:** `/api/admin/users`
- **Method:** GET
- **Role:** ADMIN | **Category:** Admin - User Management

**Mô tả:** Danh sách user: tìm username/email/họ tên, lọc role/enabled, phân trang, kèm gmailCount.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/admin/users?q=&role=&enabled=&page=&size=
- JWT + ADMIN
- `UserRepository.findAll(Specification, Pageable)`:
  - `q` → `username like %q% OR email like %q% OR fullName like %q%`
  - `role` → equal; `enabled` → equal
  - Sort: `createdAt DESC` mặc định
- Join count gmail per user (subquery `gmailAccountRepository.countByUserId`)
- Response 200 Page: `{content:[{id, username, fullName, email, role, enabled, gmailCount, createdAt}], page, size, totalElements, totalPages}`

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
