# [BE] Stream file đính kèm

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M12] File đính kèm — Tải về & Mở file |
| Owner | TV5 |
| Platform | API |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 09/11/2026 → 13/11/2026 |
| Ước lượng | 10h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Stream file đính kèm` thuộc **[M12] File đính kèm — Tải về & Mở file** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M4 GET /api/mails/{gmailAccountId}/{folder}/{uid}/attachments/{index}/download`

- **Path:** `/api/mails/{gmailAccountId}/{folder}/{uid}/attachments/{index}/download`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Stream file đính kèm thứ index, giải mã Base64, kiểm tra MAX_ATTACHMENT_SIZE_MB.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/mails/{gmailAccountId}/{folder}/{uid}/attachments/{index}/download
- Verify ownership; open folder READ_ONLY, `getMessageByUID(uid)` → null: 404
- Parse multipart → tìm part thứ `index` có `Part.ATTACHMENT` → null: 404 "Không có đính kèm"
- `part.getSize() > MAX_ATTACHMENT_SIZE_MB*1024*1024` → 413 PAYLOAD_TOO_LARGE
- Decode filename `MimeUtility.decodeText(part.getFileName())`
- Stream `part.getInputStream()` (JavaMail đã decode Base64) → response `OutputStream`
- Headers: `Content-Type=part.getContentType()`, `Content-Disposition: attachment; filename="${filename}"`, `Content-Length=part.getSize()`
- Không buffer cả file vào memory nếu có thể — dùng stream

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
