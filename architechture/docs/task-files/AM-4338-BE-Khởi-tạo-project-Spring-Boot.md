# [BE] Khởi tạo project Spring Boot

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M01] Hạ tầng Backend & Bảo mật nền tảng |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 26/10/2026 → 26/10/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Khởi tạo project Spring Boot` thuộc **[M01] Hạ tầng Backend & Bảo mật nền tảng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- Tạo project Spring Boot 3.x (Java 17+), artifact `mailapp-backend`
- Packages: `config, controller, service, repository, model, dto, security, util`
- application.yml: datasource MySQL `mailapp_db`, port 8443, SSL keystore path + password env
- Dependencies: spring-boot-starter-web, data-jpa, security, validation, mail, mysql-connector-j, jjwt, springdoc (tuỳ chọn)
- application.yml KHÔNG commit credential thật — dùng `${MAILAPP_DB_PASS}`, `${MAILAPP_AES_KEY}`, `${MAILAPP_KEYSTORE_PASS}`
- Logging: `logging.level.root=INFO`, file `logs/mailapp.log`
- Build check: `mvn spring-boot:run` boot không lỗi, `/actuator/health` trả UP trên https://localhost:8443

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
