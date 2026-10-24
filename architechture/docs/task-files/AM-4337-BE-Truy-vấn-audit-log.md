# [BE] Truy vấn audit log

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M14] Admin — Cấu hình, Thống kê & Nhật ký |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 03/11/2026 → 05/11/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Truy vấn audit log` thuộc **[M14] Admin — Cấu hình, Thống kê & Nhật ký** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-ST2 GET /api/admin/logs`

- **Path:** `/api/admin/logs`
- **Method:** GET
- **Role:** ADMIN | **Category:** Admin - Stats & Logs

**Mô tả:** Audit log lọc theo user/action/ngày, phân trang; không chứa bí mật.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/admin/logs?userId=&action=&from=&to=&page=&size=
- JWT + ADMIN
- `auditLogRepository.findAll(Specification, Pageable)` filter: userId, action contains, createdAt between
- Join user để lấy username
- Sort `createdAt DESC`
- Response Page `{content:[{id, userId, username, action, detail, ipAddress, createdAt}]}`

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M14] Admin — Cấu hình, Thống kê & Nhật ký**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
