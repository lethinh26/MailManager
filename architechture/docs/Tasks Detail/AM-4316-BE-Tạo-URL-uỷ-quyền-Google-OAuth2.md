# [BE] Tạo URL uỷ quyền Google OAuth2

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M06] Liên kết Gmail bằng OAuth2 |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 09/11/2026 → 10/11/2026 |
| Ước lượng | 4h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Tạo URL uỷ quyền Google OAuth2` thuộc **[M06] Liên kết Gmail bằng OAuth2** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-O1 GET /api/oauth2/authorize-url`

- **Path:** `/api/oauth2/authorize-url`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** OAuth2

**Mô tả:** Tạo URL uỷ quyền Google + state gắn user; kiểm tra ALLOW_OAUTH2_LINK + MAX_GMAIL_PER_USER.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/oauth2/authorize-url
- JWT required; check `ALLOW_OAUTH2_LINK` → 403 FEATURE_DISABLED
- Đếm gmail của user → ≥ MAX_GMAIL_PER_USER: 409 GMAIL_LIMIT_REACHED
- Sinh `state` = UUID.randomUUID() gắn `userId` + expiry 5 phút → lưu `OauthStateStore` (in-memory hoặc DB ttl)
- Build URL: `https://accounts.google.com/o/oauth2/v2/auth?client_id=${GOOGLE_CLIENT_ID}&redirect_uri=${REDIRECT_URI}&response_type=code&scope=https://mail.google.com/&access_type=offline&prompt=consent&state=${state}`
- Response 200 `{authorizeUrl}`
- Client: `Desktop.getDesktop().browse(URI.create(authorizeUrl))` → mở browser mặc định

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
