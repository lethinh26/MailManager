# [BE] Xoá mail qua IMAP

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M16] Xoá mail (Trash semantics) |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 04/11/2026 → 07/11/2026 |
| Ước lượng | 4h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Xoá mail qua IMAP` thuộc **[M16] Xoá mail (Trash semantics)** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M6 DELETE /api/mails/{gmailAccountId}/{folder}/{uid}`

- **Path:** `/api/mails/{gmailAccountId}/{folder}/{uid}`
- **Method:** DELETE
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Xoá mail. Folder=Trash → setFlags(\Deleted) + expunge() xoá vĩnh viễn. Folder khác → uidCopy sang [Gmail]/Trash rồi \Deleted + expunge nguồn. Ghi log MAIL_DELETE.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

DELETE /api/mails/{gmailAccountId}/{folder}/{uid}
- Verify ownership; open folder READ_WRITE
- `getMessageByUID(uid)` → null: 404
- Trash semantics Gmail:
  - Nếu `folder` là `[Gmail]/Trash` → xoá thật: `message.setFlag(Flags.Flag.DELETED, true)` + `folder.expunge()`
  - Nếu folder khác → copy sang Trash trước: `Folder trash = store.getFolder("[Gmail]/Trash"); trash.open(READ_WRITE); folder.copyMessages(new Message[]{msg}, trash); msg.setFlag(DELETED,true); folder.expunge()`
- Log `MAIL_DELETE` `{gmailId, folder, uid}` — KHÔNG log subject/body
- Response 204
- Errors: 404 UID sai, 422 NEEDS_REAUTH, 502 CONNECTION_ERROR

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M16] Xoá mail (Trash semantics)**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
