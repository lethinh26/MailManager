# [BE] Chuẩn bị Gmail test + entity

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M04] Liên kết Gmail bằng App Password |
| Owner | TV2 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 26/10/2026 → 29/10/2026 |
| Ước lượng | 2h |
| Priority | 1 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Chuẩn bị Gmail test + entity` thuộc **[M04] Liên kết Gmail bằng App Password** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `GmailAccount` entity + `GmailAccountRepository extends JpaRepository`
- Custom queries: `findByUserId(Long)`, `findByIdAndUserId(Long, Long)`, `countByUserId(Long)`, `existsByUserIdAndEmailIgnoreCase(Long, String)`
- DTO `GmailAccountResponse {id, email, displayName, authType, status, createdAt}`
- Validation schema cho `LinkAppPasswordRequest {email, appPassword}` (`@NotBlank`, `@Email`)


**Chuẩn bị Gmail test (PREP):**

- Tạo Gmail test account dùng cho dev/demo
- Bật 2FA + sinh App Password tại `myaccount.google.com/apppasswords`
- Ghi vào `.env.example` + doc `dev-setup.md` (KHÔNG commit password thật)
- Verify `javamail` `Store.connect("imap.gmail.com",993,email,appPassword)` bằng test standalone trước khi code module chính

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
