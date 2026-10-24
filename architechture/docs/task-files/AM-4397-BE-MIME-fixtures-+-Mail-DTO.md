# [BE] MIME fixtures + Mail DTO

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M08] Nhận mail — IMAP Fetch |
| Owner | TV4 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 26/10/2026 → 29/10/2026 |
| Ước lượng | 2h |
| Priority | 1 |

---

## 2. Mục tiêu

Hoàn thành `[BE] MIME fixtures + Mail DTO` thuộc **[M08] Nhận mail — IMAP Fetch** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- Định nghĩa `MailSummaryDto {uid, from, subject, date, isRead, hasAttachment}` + `MailDetailDto {uid, from, to[], cc[], bcc[], subject, date, bodyText, bodyHtml, attachments[]}`
- `MailAttachmentDto {index, fileName, contentType, sizeBytes}`
- Chuẩn bị 5+ email test: text-only, HTML-only, multipart/mixed với PDF, đính kèm nhiều file, mail có ký tự UTF-8
- Gửi các mail này vào Gmail test account để dùng cho manual test

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
