# [BE] Đăng ký tài khoản mới

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M02] Xác thực tài khoản hệ thống |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 28/10/2026 → 30/10/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Đăng ký tài khoản mới` thuộc **[M02] Xác thực tài khoản hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-A1 POST /api/auth/register`

- **Path:** `/api/auth/register`
- **Method:** POST
- **Role:** PUBLIC | **Category:** Authentication

**Mô tả:** Đăng ký tài khoản mới {username,password,fullName,email}; kiểm tra cờ ALLOW_REGISTRATION; role=USER; BCrypt; log REGISTER.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

POST /api/auth/register
- Body: `{username, password, fullName, email}`
- Validation: username 3-64 ký tự không trùng (case-insensitive), password ≥8, email hợp lệ
- Kiểm tra `SystemSettingService.getBool("ALLOW_REGISTRATION")` trước → nếu false: 403 `{code:"REGISTRATION_DISABLED"}`
- Hash password bằng BCrypt (strength 10)
- role=USER, enabled=true, must_change_password=false
- Ghi log `REGISTER`
- Response 201: `{id, username, fullName, email, role, createdAt}`
- Errors: 400 VALIDATION_ERROR (thiếu field / email sai / password yếu), 403 REGISTRATION_DISABLED, 409 username trùng
- Test: Postman → 201; gọi lại username cũ → 409; tắt flag → 403

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** POST /api/auth/register trả 201 UserDto gồm id, username, fullName, email, role, mustChangePassword, createdAt; username 3–64 chữ ASCII/số/gạch dưới; tra cứu không phân biệt hoa thường, email duy nhất chuẩn hóa. BCrypt mặc định strength 10; USER/enabled=true/mustChangePassword=false, audit REGISTER. Kiểm tra bảo trì/ALLOW_REGISTRATION trước lưu. Email và fullName tối đa 255 ký tự theo V6; username tối đa 64.
- **Khác biệt:** 400 VALIDATION_ERROR cho mật khẩu yếu hoặc vượt 72 byte UTF-8 (ít nhất 8 ký tự, có chữ và số), 409 EMAIL_TAKEN bổ sung, 503 MAINTENANCE_MODE, 429 TOO_MANY_ATTEMPTS khi đạt ngưỡng lỗi trùng từ IP; giữ mã cụ thể để Client xử lý. V5 mở rộng username và unique identity; dữ liệu cũ trùng cần xử lý trước migration.
- **Mã nguồn:** `controller/AuthController.java#register`, `service/impl/AuthServiceImpl.java#register`, `dto/RegisterRequest.java`, `dto/UserDto.java`, `repository/UserRepository.java`; `src/main/java/db/migration/V5__UserIdentityConstraints.java`.
- **Test source:** `controller/AuthRegisterAndChangePasswordTest.java` (testRegisterSuccess, testRegisterRoleSpoofingIgnored, testRegisterUsernameDuplicate, testRegisterDisabled, testLoginByEmailAndRejectDuplicateEmailRegister, utf8BcryptLimitAppliesToRegisterLoginAndChangePassword); `migration/FlywayUserIdentityMigrationTest.java`.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.


- **Bằng chứng cập nhật:** Race username/email trên MySQL native: chỉ một insert thắng, request còn lại trả 409 USERNAME_TAKEN/EMAIL_TAKEN; user thắng login đúng.
## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AuthRegisterAndChangePasswordTest: 12/12 PASS; GlobalExceptionHandlerTest: 3/3 PASS; FlywayUserIdentityMigrationTest: 1/1 PASS; ClientLiveServerE2ETest: 16/16 PASS; MySqlConcurrentAcceptanceTest: 2/2 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đúng 201/403/409 và 400 VALIDATION_ERROR; kiểm tra mật khẩu BCrypt theo byte UTF-8, V5 và test đăng ký.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Đăng ký user không dùng Gmail account.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_REGISTRATION. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (local agent, không human sign-off). [Vòng D](../backend-supplemental-observations.md): H009–011/H098: 201 USER, duplicate 409, validation 400 và REGISTER. D028–039: public flag tắt, payload hợp lệ 403 REGISTRATION_DISABLED, user count 0; restore flag, 201 USER/mustChangePassword=false; username trùng và khác hoa/thường 409 USERNAME_TAKEN, email trùng khác hoa/thường 409 EMAIL_TAKEN. D042–043: đúng một user enabled, đúng một REGISTER cùng actor ID. D040–041/D044/D047: thêm cặp đăng ký trùng cho 201/409, đúng một user và một REGISTER; không xác nhận overlap thủ công, race dựa trên MySqlConcurrentAcceptanceTest 2/2 ở mốc trước. Đủ scope manual local Postman runtime từng request của §4 gốc; không phải human sign-off.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M02] Xác thực tài khoản hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
