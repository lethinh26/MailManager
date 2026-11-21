# [BE] Danh sách folder IMAP

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M08] Nhận mail — IMAP Fetch |
| Owner | TV4 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 02/11/2026 → 04/11/2026 |
| Ước lượng | 3h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Danh sách folder IMAP` thuộc **[M08] Nhận mail — IMAP Fetch** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M1 GET /api/mails/folders`

- **Path:** `/api/mails/folders`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Danh sách folder IMAP của Gmail (name,total,unread). Query: gmailAccountId.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/mails/folders?gmailAccountId={id}
- Verify `gmailAccountRepository.findByIdAndUserId(id, userId)` → null: 404 GMAIL_NOT_OWNED
- `MailConnectionFactory.createImapStore(acc)` → `store.connect()`
- `store.getDefaultFolder().list("*")` → mỗi `Folder`: `getName()`, `open(READ_ONLY)`, `getMessageCount()`, `getUnreadMessageCount()`, close
- Response 200: `[{name, total, unread}]`
- Errors: auth fail → 422 GMAIL_NEEDS_REAUTH (OAuth2) hoặc 422 GMAIL_AUTH_FAILED (App Password); network → 502 GMAIL_CONNECTION_ERROR
- Finally `store.close()`

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M08] Nhận mail — IMAP Fetch**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
