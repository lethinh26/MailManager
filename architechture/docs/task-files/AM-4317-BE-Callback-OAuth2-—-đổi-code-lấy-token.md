# [BE] Callback OAuth2 — đổi code lấy token

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M06] Liên kết Gmail bằng OAuth2 |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 10/11/2026 → 13/11/2026 |
| Ước lượng | 6h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Callback OAuth2 — đổi code lấy token` thuộc **[M06] Liên kết Gmail bằng OAuth2** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-O2 GET /api/oauth2/callback`

- **Path:** `/api/oauth2/callback`
- **Method:** GET
- **Role:** PUBLIC | **Category:** OAuth2

**Mô tả:** Google callback: đổi code -> token, lấy email, lưu refresh_token mã hoá; trả HTML kết quả.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/oauth2/callback?code=&state=
- KHÔNG JWT — xác thực bằng `state` lookup trong store (expired/invalid → HTML lỗi)
- POST `https://oauth2.googleapis.com/token` body `code, client_id, client_secret, redirect_uri, grant_type=authorization_code` → parse `access_token + refresh_token + expires_in`
- GET `https://gmail.googleapis.com/gmail/v1/users/me/profile` Bearer access_token → lấy `emailAddress`
- Kiểm tra trùng `existsByUserIdAndEmailIgnoreCase` → HTML "Gmail này đã liên kết"
- `AesCipher.encrypt(refreshToken)` → save `GmailAccount{authType=OAUTH2, refreshTokenEncrypted, status=ACTIVE}`
- Xoá state khỏi store (one-time use)
- Log `OAUTH2_CALLBACK` `{email}`
- Response `Content-Type: text/html` đơn giản "Liên kết Gmail thành công — quay lại ứng dụng" (không redirect vì không có deep-link)

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M06] Liên kết Gmail bằng OAuth2**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
