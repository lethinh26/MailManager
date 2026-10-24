# [BE] Chi tiết mail + parse MIME

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M08] Nhận mail — IMAP Fetch |
| Owner | TV4 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 04/11/2026 → 07/11/2026 |
| Ước lượng | 4.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Chi tiết mail + parse MIME` thuộc **[M08] Nhận mail — IMAP Fetch** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M3 GET /api/mails/{gmailAccountId}/{folder}/{uid}`

- **Path:** `/api/mails/{gmailAccountId}/{folder}/{uid}`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Chi tiết mail: body text/html + metadata đính kèm; mở READ_WRITE đặt cờ \Seen.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/mails/{gmailAccountId}/{folder}/{uid}
- Verify ownership, URL-decode folder, open READ_WRITE (để set \Seen)
- `UIDFolder.getMessageByUID(uid)` → null: 404
- Parse message: `getFrom()[0]`, `getAllRecipients()` split TO/CC/BCC, `getSubject()`, `getReceivedDate()`
- Parse content recursive (`Multipart`):
  - `text/plain` → `bodyText`
  - `text/html` → `bodyHtml`
  - `Part.ATTACHMENT` → metadata `{index, fileName=MimeUtility.decodeText, contentType, sizeBytes=getSize()}` — KHÔNG đọc content
- Sau khi parse → `message.setFlag(Flags.Flag.SEEN, true)` → folder đóng sẽ commit
- Response 200: `{uid, from, to[], cc[], bcc[], subject, date, bodyText, bodyHtml, attachments[]}`

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
