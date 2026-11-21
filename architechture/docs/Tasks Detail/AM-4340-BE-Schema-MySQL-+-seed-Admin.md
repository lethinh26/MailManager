# [BE] Schema MySQL + seed Admin

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M01] Hạ tầng Backend & Bảo mật nền tảng |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 26/10/2026 → 27/10/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Schema MySQL + seed Admin` thuộc **[M01] Hạ tầng Backend & Bảo mật nền tảng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- Tạo database `mailapp_db` charset utf8mb4 collation utf8mb4_unicode_ci
- Bảng `users`: id BIGINT PK AI, username VARCHAR(64) UNIQUE, password VARCHAR(255), full_name VARCHAR(255), email VARCHAR(255), role ENUM('USER','ADMIN'), enabled BOOLEAN, must_change_password BOOLEAN, created_at, updated_at
- Bảng `gmail_accounts`: id, user_id FK users.id, email VARCHAR(255), display_name, auth_type ENUM('APP_PASSWORD','OAUTH2'), app_password_encrypted TEXT, refresh_token_encrypted TEXT, status ENUM('ACTIVE','NEEDS_REAUTH','ERROR'), created_at
- Bảng `system_settings`: key VARCHAR(64) PK, value TEXT, description, updated_by, updated_at
- Bảng `audit_logs`: id, user_id FK, action VARCHAR(64), detail TEXT, ip_address VARCHAR(64), created_at
- Migration script `V1__init.sql` (hoặc để JPA tạo + script seed riêng)
- Seed admin: `username=admin`, password=`Admin@123` (BCrypt), role=ADMIN, must_change_password=true — log ra console để TV1 cung cấp cho nhóm
- Seed 9 khoá `system_settings`: ALLOW_REGISTRATION=true, ALLOW_OAUTH2_LINK=true, ALLOW_APP_PASSWORD_LINK=true, ALLOW_SEND_MAIL=true, MAINTENANCE_MODE=false, MAX_GMAIL_PER_USER=5, MAX_ATTACHMENT_SIZE_MB=25, MAIL_PAGE_SIZE=20, LOGIN_MAX_FAILED_ATTEMPTS=5

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M01] Hạ tầng Backend & Bảo mật nền tảng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
