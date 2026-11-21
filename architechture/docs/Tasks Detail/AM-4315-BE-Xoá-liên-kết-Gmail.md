# [BE] Xoá liên kết Gmail

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M04] Liên kết Gmail bằng App Password |
| Owner | TV2 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 05/11/2026 → 06/11/2026 |
| Ước lượng | 2.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Xoá liên kết Gmail` thuộc **[M04] Liên kết Gmail bằng App Password** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-G3 DELETE /api/gmail-accounts/{id}`

- **Path:** `/api/gmail-accounts/{id}`
- **Method:** DELETE
- **Role:** AUTHENTICATED | **Category:** Gmail Account

**Mô tả:** Xoá liên kết Gmail; OAuth2 thì revoke token trước; log UNLINK_GMAIL.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

DELETE /api/gmail-accounts/{id}
- JWT required; tìm `gmailAccountRepository.findByIdAndUserId(id, userId)` → không thấy → 404 NOT_FOUND
- Nếu `authType=OAUTH2`: gọi `OAuth2Service.revoke(refreshToken)` của TV3 — bọc try/catch, revoke lỗi vẫn xoá (log warn)
- Delete record (KHÔNG xoá mail trên Gmail — Gmail giữ nguyên)
- Log `UNLINK_GMAIL` `{email, authType}`
- Response 204 No Content
- Test: xoá Gmail của mình → 204; xoá id không tồn tại → 404; xoá của user khác → 404 (không lộ info)

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M04] Liên kết Gmail bằng App Password**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
