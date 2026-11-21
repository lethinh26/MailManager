# [BE] Toggle cờ starred/seen qua IMAP

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M17] Gắn sao & đánh dấu đã/chưa đọc (flags) |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 06/11/2026 → 08/11/2026 |
| Ước lượng | 3h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Toggle cờ starred/seen qua IMAP` thuộc **[M17] Gắn sao & đánh dấu đã/chưa đọc (flags)** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M7 PUT /api/mails/{gmailAccountId}/{folder}/{uid}/flags`

- **Path:** `/api/mails/{gmailAccountId}/{folder}/{uid}/flags`
- **Method:** PUT
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Toggle cờ mail {starred, seen} → IMAP setFlags \Flagged / \Seen. Trả {uid, starred, seen}. Cung cấp luôn mark-unread.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

PUT /api/mails/{gmailAccountId}/{folder}/{uid}/flags
- Body `{starred?: boolean, seen?: boolean}` — ít nhất 1 field
- Open folder READ_WRITE; `getMessageByUID`
- `starred` → `Flags.Flag.FLAGGED`; `seen` → `Flags.Flag.SEEN`
- `message.setFlag(flag, value)`; đóng folder commit
- Log `MAIL_FLAG_CHANGE` `{gmailId, uid, starred, seen}`
- Response 200 `{uid, starred, seen}` — trạng thái mới sau khi set

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M17] Gắn sao & đánh dấu đã/chưa đọc (flags)**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
