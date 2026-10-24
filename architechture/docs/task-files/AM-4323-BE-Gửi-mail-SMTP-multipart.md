# [BE] Gửi mail SMTP multipart

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M10] Gửi mail — SMTP |
| Owner | TV5 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 03/11/2026 → 06/11/2026 |
| Ước lượng | 8h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Gửi mail SMTP multipart` thuộc **[M10] Gửi mail — SMTP** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M5 POST /api/mails/send`

- **Path:** `/api/mails/send`
- **Method:** POST
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Gửi mail SMTP STARTTLS multipart (to,cc,bcc,subject,body,isHtml,files[]); kiểm tra ALLOW_SEND_MAIL; log SEND_MAIL.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

POST /api/mails/send (multipart/form-data)
- Fields: `gmailAccountId`, `to`, `cc`, `bcc`, `subject`, `body`, `isHtml` (bool), `files[]` (MultipartFile 0..n)
- Verify ownership + check `ALLOW_SEND_MAIL` → 403 FEATURE_DISABLED
- Validate: `to` email hợp lệ, `subject` ≤ 200 chars, `body` ≤ 1MB
- Từng file: `file.getSize() > MAX_ATTACHMENT_SIZE_MB*1024*1024` → 413 PAYLOAD_TOO_LARGE
- Dựng `MimeMessage`:
  - `setFrom(acc.email)`, `setRecipients(TO/CC/BCC)`, `setSubject(subject,"UTF-8")`
  - Nếu có files: `Multipart` = `MimeBodyPart(body)` + `MimeBodyPart(file)` mỗi file (`attachFile`, `MimeUtility.encodeText(filename,"UTF-8","B")`)
  - Else: `setContent(body, isHtml?"text/html; charset=utf-8":"text/plain; charset=utf-8")`
- `MailConnectionFactory.createSmtpTransport(acc)` → `transport.connect()` → `transport.sendMessage(msg, msg.getAllRecipients())` → close
- Log `SEND_MAIL` `{to, subject, attachmentCount}` — KHÔNG log body
- Response 200 `{message:"Sent", sentAt}`
- Errors: auth → 422; network → 502 GMAIL_CONNECTION_ERROR; address sai → 400 với message từ `SendFailedException`

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M10] Gửi mail — SMTP**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
