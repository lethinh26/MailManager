# [BE] Liên kết Gmail bằng App Password

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M04] Liên kết Gmail bằng App Password |
| Owner | TV2 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 02/11/2026 → 04/11/2026 |
| Ước lượng | 4h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Liên kết Gmail bằng App Password` thuộc **[M04] Liên kết Gmail bằng App Password** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-G2 POST /api/gmail-accounts/app-password`

- **Path:** `/api/gmail-accounts/app-password`
- **Method:** POST
- **Role:** AUTHENTICATED | **Category:** Gmail Account

**Mô tả:** Liên kết Gmail bằng App Password {email,appPassword}; test IMAP thật trước khi lưu; AES-256-GCM; log LINK_GMAIL.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

POST /api/gmail-accounts/app-password
- Body `{email, appPassword}` — validate format email
- Kiểm tra cờ `ALLOW_APP_PASSWORD_LINK` → false: 403 FEATURE_DISABLED
- Kiểm tra trùng email (theo user hiện tại) → 409 GMAIL_DUPLICATE
- Đếm `countByUserId` ≥ `MAX_GMAIL_PER_USER` → 409 GMAIL_LIMIT_REACHED
- **Test kết nối thật**: `MailConnectionFactory.createStore(EMAIL, APP_PASSWORD)` → `store.connect("imap.gmail.com", 993, email, appPassword)` → timeout 10s
  - Sai password → `AuthenticationFailedException` → 422 GMAIL_AUTH_FAILED
  - Network/DNS lỗi → `MessagingException` → 502 GMAIL_CONNECTION_ERROR
- Đóng store trong finally
- Đúng → `AesCipher.encrypt(appPassword)` → save `GmailAccount{userId, email, displayName=email, authType=APP_PASSWORD, appPasswordEncrypted, status=ACTIVE}`
- Log `LINK_GMAIL` detail `{email}`
- Response 201 `{id, email, authType, status}`

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
