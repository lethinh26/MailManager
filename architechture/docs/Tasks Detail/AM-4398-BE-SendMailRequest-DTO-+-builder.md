# [BE] SendMailRequest DTO + builder

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M10] Gửi mail — SMTP |
| Owner | TV5 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 27/10/2026 → 30/10/2026 |
| Ước lượng | 2h |
| Priority | 1 |

---

## 2. Mục tiêu

Hoàn thành `[BE] SendMailRequest DTO + builder` thuộc **[M10] Gửi mail — SMTP** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- Contract `SendMailRequest {gmailAccountId, to, cc, bcc, subject, body, isHtml}` (DTO phần fields, files truyền riêng `MultipartFile[]`)
- Test `MimeMessage` basic bằng `javax.mail` đứng một mình: build message với body text + 1 attachment → verify `msg.getContent()` parse đúng
- Chuẩn bị test data: file PDF nhỏ + ảnh PNG + file text để test đa dạng MIME type

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
