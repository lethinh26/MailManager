# [FE] Tự mở file sau khi tải + nhớ file đã tải

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M12] File đính kèm — Tải về & Mở file |
| Owner | TV5 |
| Platform | UserWeb |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 12/11/2026 → 13/11/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Tự mở file sau khi tải + nhớ file đã tải` thuộc **[M12] File đính kèm — Tải về & Mở file** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `FileUtil.download(ApiClient, gmailId, folder, uid, index, fileName)`:
  - Gọi EP-M4 → `byte[]` hoặc `InputStream`
  - Thư mục `%USERPROFILE%\Downloads\MailApp\${gmailAccountEmail}\` (tạo nếu chưa có)
  - Chống trùng: nếu `filename.ext` tồn tại → `filename (1).ext`, `(2)`,...
  - Ghi file, lưu map `attachmentKey → localPath` vào `SessionStore.attachmentCache` (in-memory)
  - Trả `File` object
- `FileUtil.openOrDownload(...)`: nếu attachment đã trong cache + file tồn tại → `Desktop.getDesktop().open(file)` — không tải lại
- Caller: `MailDetailPanel` nút click attachment

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M12] File đính kèm — Tải về & Mở file**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
