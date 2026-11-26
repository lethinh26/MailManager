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

> **Điều chỉnh đặc tả 26/11/2026 — chủ dự án đã phê duyệt:** Sau đối chiếu ba khác biệt contract, chủ dự án yêu cầu sửa Task Detail theo phương án hiện hành. §4 dưới đây là yêu cầu áp dụng; ghi chú/evidence G6 trở về trước là lịch sử. Phê duyệt không thay manual/live evidence hoặc giảm guard bảo mật.

- Tạo database `mailapp_db` charset utf8mb4 collation utf8mb4_unicode_ci
- Bảng `users`: id BIGINT PK AI, username VARCHAR(64) UNIQUE, password_hash VARCHAR(255), full_name VARCHAR(255), email VARCHAR(255), role VARCHAR kết hợp Java enum USER/ADMIN, enabled BOOLEAN, must_change_password BOOLEAN, created_at, updated_at
- Bảng `gmail_accounts`: id, user_id FK users.id, email VARCHAR(255), display_name, auth_type VARCHAR kết hợp Java enum APP_PASSWORD/OAUTH2, app_password_encrypted TEXT, refresh_token_encrypted TEXT, status VARCHAR kết hợp Java enum ACTIVE/NEEDS_REAUTH/ERROR, created_at; các migration additive cho phép thêm email_key, dual-auth/fallback, metadata, last_verified_at và revision; giữ owner/FK/unique, không làm mất ciphertext.
- Bảng `system_settings`: setting_key VARCHAR(100) PK, setting_value TEXT, description, updated_by, updated_at; bảo toàn setting đã lưu.
- Bảng `audit_logs`: id, user_id FK, action VARCHAR(64), detail TEXT, ip_address VARCHAR(64), created_at
- Flyway V1__init.sql và migration additive V2–V9: giữ bốn bảng nghiệp vụ và bổ sung `mail_audit_outbox` cho audit recovery, index/correlation/revision; JPA validate, không sửa migration đã áp dụng, không thu hẹp cột hay làm mất dữ liệu. Gmail FK cascade; audit/settings giữ lịch sử khi xóa user. Kiểm chứng schema mới và upgrade legacy bằng MySQL cô lập.
- Seed Admin duy nhất khi chưa tồn tại user role ADMIN: username admin, enabled=true, role=ADMIN, must_change_password=true; mật khẩu từ `ADMIN_INITIAL_PASSWORD` hoặc cấu hình tương ứng (`mailapp.admin.initial-password`), lưu BCrypt vào password_hash. Thiếu mật khẩu thì từ chối khởi tạo; môi trường non-dev/test kiểm ít nhất 8 ký tự có chữ và số. Không hardcode, không in/log plaintext, không ghi đè Admin đã tồn tại. Fixture có thể cấp mật khẩu ngẫu nhiên riêng; initializer không tự sinh mật khẩu.
- Seed 9 khoá `system_settings`: ALLOW_REGISTRATION=true, ALLOW_OAUTH2_LINK=true, ALLOW_APP_PASSWORD_LINK=true, ALLOW_SEND_MAIL=true, MAINTENANCE_MODE=false, MAX_GMAIL_PER_USER=5, MAX_ATTACHMENT_SIZE_MB=25, MAIL_PAGE_SIZE=20, LOGIN_MAX_FAILED_ATTEMPTS=5

> **Lịch sử contract:** §4 cũ mô tả password/ENUM/key VARCHAR(64), đúng bốn bảng và seed bằng mật khẩu cố định in console. Chủ dự án chấp thuận schema mở rộng và bootstrap an toàn thay thế; không lặp lại giá trị mật khẩu cũ. Giữ đủ chức năng bốn bảng, chín settings, BCrypt, forced-change và bảo toàn dữ liệu. Đây là phê duyệt contract, không phải chạy migration production.

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** Flyway V1 bốn bảng, V2 last_verified_at, V3 email_key/đa phương thức + avatar/fallback/unique owner, V4 bổ sung đủ 9 setting thiếu mà không ghi đè, V5 username VARCHAR(64) + unique username_ci/email chuẩn hóa. FK cascade Gmail, audit/settings giữ bản ghi khi xóa user. JPA validate schema. AdminAccountInitializer chỉ tạo Admin khi chưa có, BCrypt + mustChangePassword=true, password từ ADMIN_INITIAL_PASSWORD. V6 chỉ mở rộng các cột: user full_name/email và Gmail email/email_key/display_name VARCHAR(255), setting_value và audit detail TEXT, audit action/IP VARCHAR(64), giữ FK/unique/dữ liệu hiện hữu; model hỗ trợ trạng thái ERROR cũ.
- **Khác biệt bảo mật/schema:** không dùng/log mật khẩu Admin cố định; password_hash thay password; role/auth/status VARCHAR + Java enum; setting_key giữ 100 thay vì thu hẹp; audit detail vẫn giới hạn payload nghiệp vụ 500 để tránh log quá dài. V6 không đổi tên cột, không sửa/xóa dữ liệu hay migration đã áp dụng. MAIL_PAGE_SIZE mặc định 20 khi chưa có, V4 không ghi đè cấu hình đã lưu. V5 dừng nếu identity cũ trùng, không tự gộp/xóa. `docker-compose.yml` đặt --character-set-server=utf8mb4 và --collation-server=utf8mb4_unicode_ci cho DB mới; không chuyển đổi DB/volume đã có. MySQL native cô lập đã xác minh charset/collation; không suy rộng sang volume hiện hữu.
- **Mã nguồn:** `src/main/resources/db/migration/V1__init.sql`, V2, V4; `src/main/java/db/migration/V3__GmailEmailKeyMultiAuth.java`, `V5__UserIdentityConstraints.java`; `model/User.java`, GmailAccount/SystemSetting/AuditLog, `config/AdminAccountInitializer.java`, `docker-compose.yml`.
- **Test source:** `migration/FlywayV3MigrationTest.java`, `migration/FlywayUserIdentityMigrationTest.java` (H2 MySQL mode, 9 keys/unique/widen), `config/AdminAccountInitializerTest.java` (bảo toàn admin/thiếu và yếu password). Đã kiểm chứng MySQL native mới migrate/charset/seed an toàn; không đổi dữ liệu production trong lần này. FlywayUserIdentityMigrationTest bổ sung kiểm tra dữ liệu/cipher cũ vẫn còn, các cột mở rộng, giá trị TEXT và trạng thái ERROR.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.


- **Bằng chứng cập nhật:** MySQL native 9.5.0, datadir mới cô lập, utf8mb4/utf8mb4_unicode_ci, Flyway V1–V6 thành công, đủ 4 bảng nghiệp vụ, 9 settings và admin bootstrap; race username/email và race cập nhật hai link flags qua HTTPS đã đạt. Flyway cảnh báo 9.5 mới hơn phạm vi hỗ trợ chính thức (8.1).

- **Bổ sung vòng G2 (sau F):** Migration V9 thêm revision BIGINT NOT NULL DEFAULT 0; native MySQL 9.5 V8→V9 trên JAR cuối bảo toàn owner, dual-method, trạng thái, ciphertext synthetic; V1–V9 fresh. Không thay bootstrap, không tự duyệt literal schema/password/console.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminAccountInitializerTest: 3/3 PASS; FlywayUserIdentityMigrationTest: 1/1 PASS; FlywayV3MigrationTest: 1/1 PASS; EndpointSecurityAuditTest: 3/3 PASS; ClientLiveServerE2ETest: 16/16 PASS; MySqlConcurrentAcceptanceTest: 2/2 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (phê duyệt đặc tả + evidence code/MySQL cô lập kế thừa). **26/11/2026:** chủ dự án chấp thuận 4340-C.03: bootstrap mật khẩu cấu hình/BCrypt/forced-change, không plaintext console, schema V1–V9 additive. 4340-C.01/C.02 đã có K/M/K; G5 fresh MySQL đủ 9 settings/1 admin/5 bảng, G2 V8→V9 giữ dữ liệu. Đủ cả ba nghĩa vụ theo contract hiện hành; đóng task. Không có test mới hay kiểm DB production. [Ma trận](../acceptance-obligation-matrix.md#am-4340-c). **Lịch sử G6 trở về trước:** V1–V6 bảo toàn schema/dữ liệu, admin bootstrap ngẫu nhiên/hash BCrypt, không seed/log mật khẩu cố định; metadata aggregate fixture tách riêng. Cần chấp thuận khác biệt literal schema/seed an toàn và xác minh môi trường triển khai/legacy. Vòng C: [BE](../backend-supplemental-observations.md): V7 chỉ thêm bảng mail_audit_outbox và index; C startup native MySQL V1–V7 thành công, 4 bảng nghiệp vụ cũ + outbox, 9 settings/1 admin. Không seed/log password cố định; contract OPEN. V7-L: Native MySQL database-only, legacy V1/V2 seed synthetic → V6 làm tiền đề → V7. Snapshot 4 bảng trước/sau V7 khớp; outbox mới rỗng, 7 migration success; khởi động JAR vẫn giữ nguyên dữ liệu, 9 settings/1 admin/5 bảng (4 cũ + outbox). Exit 0, datadir fixture đã xóa. Lý do regression riêng: V7 vừa thêm và legacy harness phải nhận version 7; không phải nghiệm thu lại các task migration/AES/TLS cũ. Client/Newman không chạy. Evidence Mail Server/build/acceptance/legacy-upgrade-summary-V7.txt và mysql-metadata-V7-legacy.tsv. Không thay literal contract. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): V8 additive native V7→V8: historical audit và PREPARED giữ nguyên, không tạo success giả; V1–V8 đều success. Không seed/log password cố định; literal contract chưa duyệt. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4340-C ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4340-c).
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Schema/seed infrastructure, không hành vi mail cụ thể; endpoint vẫn phải bảo vệ.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Schema/seed infrastructure, không hành vi mail cụ thể; endpoint vẫn phải bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Schema/seed infrastructure, không hành vi mail cụ thể; endpoint vẫn phải bảo vệ.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (phạm vi local đã kiểm chứng). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4340 (H refs trong hàng AM-4340, NOT OBSERVED): H006 API settings đủ 9, H035 KPI có admin/users; không phải quan sát schema/DDL hoặc seed DB trực tiếp. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) L01/L02/L04: native MySQL 9.5.0 từ V2 có dữ liệu lên V6, users 2→2, aliases 3→2 giữ hai auth methods và owner độc lập, audit 1→2, settings 7→9 giữ custom flags/quota; 4 insert sai bị reject; V5 duplicate email fail trước DDL, hai user nguyên vẹn; startup JAR không đổi row snapshot; port 8443 TLS tin cậy. Phần còn lại hiện tại: Đủ kiểm chứng operational local schema/migration/startup, không phải JUnit thay thao tác. Contract literal schema/seed vẫn OPEN riêng vì chưa có phê duyệt. Không kiểm DB triển khai hiện hữu. Tách riêng human sign-off, live, contract và kết quả tự động. Vòng C: [BE](../backend-supplemental-observations.md): V7 chỉ thêm bảng mail_audit_outbox và index; C startup native MySQL V1–V7 thành công, 4 bảng nghiệp vụ cũ + outbox, 9 settings/1 admin. Không seed/log password cố định; contract OPEN. **Vòng G2:** Migration V9 thêm revision BIGINT NOT NULL DEFAULT 0; native MySQL 9.5 V8→V9 trên JAR cuối bảo toàn owner, dual-method, trạng thái, ciphertext synthetic; V1–V9 fresh. Không thay bootstrap, không tự duyệt literal schema/password/console. Không đổi checkbox.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M01] Hạ tầng Backend & Bảo mật nền tảng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
