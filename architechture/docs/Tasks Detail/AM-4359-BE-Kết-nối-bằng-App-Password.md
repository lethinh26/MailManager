# [BE] Kết nối bằng App Password

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M07] MailConnectionFactory — kết nối Gmail dùng chung |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 27/10/2026 → 28/10/2026 |
| Ước lượng | 2.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Kết nối bằng App Password` thuộc **[M07] MailConnectionFactory — kết nối Gmail dùng chung** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `MailConnectionFactory` interface:
  - `Store createImapStore(GmailAccount acc)` — `imaps://imap.gmail.com:993`, auth theo `authType`
  - `Transport createSmtpTransport(GmailAccount acc)` — `smtp.gmail.com:587` STARTTLS
  - `Session createSession(GmailAccount acc, Protocol proto)`
- App Password impl: `session.setPasswordAuthentication(new PasswordAuthentication(email, decrypt(appPasswordEncrypted)))`
- OAuth2 impl: dùng `MailConnectionFactory.getValidAccessToken(acc)` → set SASL XOAUTH2 (`OAuth2SaslClientFactory`) hoặc `mail.imaps.sasl.mechanisms=XOAUTH2`
- Timeout thống nhất: `mail.imaps.connectiontimeout=10000`, `mail.imaps.timeout=15000`, `mail.smtp.connectiontimeout=10000`
- TLS: `mail.imaps.ssl.enable=true`, `mail.smtp.starttls.enable=true`, `mail.smtp.ssl.trust=smtp.gmail.com`
- `GmailAuthenticationException` wrap các lỗi auth

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M07] MailConnectionFactory — kết nối Gmail dùng chung**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
