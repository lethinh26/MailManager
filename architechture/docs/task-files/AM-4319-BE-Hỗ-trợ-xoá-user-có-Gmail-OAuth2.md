# [BE] Hỗ trợ xoá user có Gmail OAuth2

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M06] Liên kết Gmail bằng OAuth2 |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 05/11/2026 → 07/11/2026 |
| Ước lượng | 2h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Hỗ trợ xoá user có Gmail OAuth2` thuộc **[M06] Liên kết Gmail bằng OAuth2** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-AD8 DELETE /api/admin/users/{id}`

- **Path:** `/api/admin/users/{id}`
- **Method:** DELETE
- **Role:** ADMIN | **Category:** Admin - User Management

**Mô tả:** Xoá user + toàn bộ Gmail liên kết (revoke OAuth2 trước).

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

DELETE /api/admin/users/{id}
- Chặn tự xoá chính mình; chặn xoá Admin cuối cùng
- `gmailAccountRepository.findByUserId(id)` → với mỗi cái authType=OAUTH2 → `OAuth2Service.revoke` (best-effort)
- Xoá gmail records → xoá user (cascade hoặc tay)
- Log `ADMIN_DELETE_USER` `{targetUserId, username, gmailCount}`
- Response 204

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M06] Liên kết Gmail bằng OAuth2**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
