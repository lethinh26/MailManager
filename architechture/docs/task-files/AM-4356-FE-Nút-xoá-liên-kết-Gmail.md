# [FE] Nút xoá liên kết Gmail

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M05] Màn hình Quản lý Gmail |
| Owner | TV2 |
| Platform | UserWeb |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 06/11/2026 → 07/11/2026 |
| Ước lượng | 0.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Nút xoá liên kết Gmail` thuộc **[M05] Màn hình Quản lý Gmail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `GmailAccountDialog extends JDialog` (modal, owner=MainFrame)
- `JTable` columns: Email | Display Name | Auth Type | Status | Created At — row từ EP-G1
- Form nhỏ bên dưới: input Email + App Password (JPasswordField, hint "Chỉ dùng Gmail đã bật 2FA + App Password từ myaccount.google.com")
- Nút "Liên kết App Password" → EP-G2 (SwingWorker); "Liên kết Google OAuth2" → EP-O1 rồi `Desktop.browse(authorizeUrl)`; "Xoá" → confirm dialog → EP-G3
- Theo `publicConfig`: `appPasswordLinkEnabled=false` → disable form App Password + show note; `oauth2LinkEnabled=false` → disable nút OAuth2
- Status RED "NEEDS_REAUTH" → nút "Liên kết lại" (chỉ OAuth2) gọi EP-O1 với email cũ

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M05] Màn hình Quản lý Gmail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
