# [BE] Cập nhật cấu hình hệ thống

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M14] Admin — Cấu hình, Thống kê & Nhật ký |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 29/10/2026 → 01/11/2026 |
| Ước lượng | 3h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Cập nhật cấu hình hệ thống` thuộc **[M14] Admin — Cấu hình, Thống kê & Nhật ký** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-S2 PUT /api/admin/settings`

- **Path:** `/api/admin/settings`
- **Method:** PUT
- **Role:** ADMIN | **Category:** Admin - Settings

**Mô tả:** Cập nhật nhiều khoá; validate; cấm tắt cả hai cách liên kết; log ADMIN_CHANGE_SETTING.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

PUT /api/admin/settings
- JWT + ADMIN; body `{settings: {"KEY":"value", ...}}`
- Validate từng key: `ALLOW_*` là boolean string, `MAX_*`/`MAIL_PAGE_SIZE`/`LOGIN_MAX_FAILED_ATTEMPTS` là int positive
- **Ràng buộc quan trọng**: sau update, KHÔNG được `ALLOW_OAUTH2_LINK=false` && `ALLOW_APP_PASSWORD_LINK=false` cùng lúc → 400 VALIDATION_ERROR
- Update từng key, `updatedBy=currentUser.username`, `updatedAt=now`
- Log `ADMIN_CHANGE_SETTING` cho từng khoá đổi: `{key, oldValue, newValue}`
- Invalidate cache `SystemSettingService`
- Response 200: danh sách settings mới

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
