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

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** Spring Boot 3.3.5, Java 21, Gradle wrapper, project mail-server; packages và dependencies web/JPA/security/validation/mail/MySQL/JJWT/springdoc/actuator/Flyway. application.properties: MySQL mailapp_db, HTTPS 8443, root INFO, logs/mailapp.log. Env MAILAPP_DB_PASS/MAILAPP_AES_KEY/MAILAPP_KEYSTORE_PASS/MAILAPP_JWT_SECRET và alias DB_PASSWORD/AES_KEY/KEYSTORE_PASSWORD/JWT_SECRET tương thích triển khai cũ.
- **Khác biệt:** Gradle (bootRun thay mvn spring-boot:run), Java 21, artifact mail-server và .properties thay YAML là cấu trúc hiện hữu, không cần đổi để đáp ứng chức năng. Không đưa credential thật vào tài liệu/config.
- **Mã nguồn:** `build.gradle`, `settings.gradle`, `src/main/resources/application.properties`, `src/main/resources/application-dev.properties`, `controller/PublicHealthController.java`, `security/SecurityStartupValidator.java`.
- **Test source:** `security/NonDevProfileTest.java`, `security/EndpointSecurityAuditTest.java`, các SpringBootTest. Boot server với MySQL và HTTPS /actuator/health UP chưa xác nhận thủ công; không đồng nhất test H2/MockMvc với runtime thật.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.


- **Bằng chứng cập nhật:** JAR chạy HTTPS trên MySQL native V1–V6; bỏ UserDetailsServiceAutoConfiguration không sinh/log mật khẩu Spring mặc định.
## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** EndpointSecurityAuditTest: 3/3 PASS; NonDevProfileTest: 3/3 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Khởi tạo project/infrastructure; quyền, flag, audit tại endpoint/caller.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Khởi tạo project/infrastructure; quyền, flag, audit tại endpoint/caller.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Khởi tạo project/infrastructure; quyền, flag, audit tại endpoint/caller.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (phạm vi local đã kiểm chứng). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4338 (H refs trong hàng AM-4338, PARTIAL): H008 GET /actuator/health 200 UP; các request HTTPS của snapshot thực tế hoạt động. Retest bản cuối: [F001](../backend-final-observations.md) (OBSERVED): GET /actuator/health qua HTTPS/Postman không JWT 200 JSON status=UP trên JAR cuối; H008 cũng UP trên phiên cũ.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) L04: JAR mới chạy trên schema V6 đã nâng từ V2, startup giữ nguyên row snapshot; HTTPS owned 8443. Phần còn lại hiện tại: Giữ PASS local, chưa deploy. Tách riêng human sign-off, live, contract và kết quả tự động.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M01] Hạ tầng Backend & Bảo mật nền tảng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
