# [BE] Admin tạo tài khoản hộ

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M13] Admin — Quản lý người dùng |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 29/10/2026 → 31/10/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Admin tạo tài khoản hộ` thuộc **[M13] Admin — Quản lý người dùng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-AD1 POST /api/admin/users`

- **Path:** `/api/admin/users`
- **Method:** POST
- **Role:** ADMIN | **Category:** Admin - User Management

**Mô tả:** Admin tạo tài khoản hộ — không bị chặn bởi ALLOW_REGISTRATION; log ADMIN_CREATE_USER.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

POST /api/admin/users
- JWT + role=ADMIN
- Body `{username, password, fullName, email, role?}` — role mặc định USER
- KHÔNG bị chặn bởi `ALLOW_REGISTRATION` — admin tạo hộ
- Validate như EP-A1; BCrypt; `must_change_password=true` để user đổi lần đầu
- Log `ADMIN_CREATE_USER` `{username, role}`
- 201: `{id, username, fullName, email, role, enabled:true, mustChangePassword:true}`
- 409 username trùng

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** POST /api/admin/users ADMIN, không phụ thuộc ALLOW_REGISTRATION; username 3–64 ASCII không trùng hoa thường, email chuẩn hóa duy nhất, role mặc định USER, BCrypt, enabled=true/mustChangePassword=true; audit ADMIN_CREATE_USER. 409 USERNAME_TAKEN/EMAIL_TAKEN. Email/fullName tối đa 255 theo V6; username giữ tối đa 64.
- **Khác biệt:** password tùy chọn; nếu bỏ trống sinh mật khẩu tạm 14 ký tự bằng SecureRandom, trả một lần trong temporaryPassword; nếu admin cung cấp password hợp lệ (ít nhất 8 ký tự, tối đa 72 byte UTF-8, có chữ/số), temporaryPassword=null. 201 mở rộng thêm gmailCount/createdAt/updatedAt và no-store. Không lưu/log mật khẩu thô.
- **Mã nguồn:** `controller/AdminController.java#createUser`, `service/impl/AdminServiceImpl.java#createUser`, `dto/admin/AdminDtos.java`, `repository/UserRepository.java`, `src/main/java/db/migration/V5__UserIdentityConstraints.java`.
- **Test source:** `controller/AdminIntegrationTest.java` (testRbac_Unauthenticated401AndRegularUser403, testCreateUser_WorksWhenRegistrationDisabled, adminCannotCreateDuplicateEmailOrCaseVariantUsername), `migration/FlywayUserIdentityMigrationTest.java`.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.


- **Bằng chứng cập nhật:** Race MySQL của đăng ký kiểm DB unique; GlobalExceptionHandler nhận cả index cũ users.username và mới uk_users_username_ci cho API tạo user. Admin create/duplicate qua Newman.
## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; GlobalExceptionHandlerTest: 3/3 PASS; FlywayUserIdentityMigrationTest: 1/1 PASS; ClientLiveServerE2ETest: 16/16 PASS; MySqlConcurrentAcceptanceTest: 2/2 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Admin tạo user theo role không truy cập mailbox; không bị ALLOW_REGISTRATION chặn.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). Cờ/giới hạn hoặc bảo trì được đối chiếu tại §4.1; server là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (phạm vi local đã kiểm chứng). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4325 (H refs trong hàng AM-4325, PARTIAL): H073 admin tạo 201 id 7 USER, mustChangePassword=true, temporaryPassword chỉ [present], no-store; H074 list xác nhận metadata không có mật khẩu tạm. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G009–012/G015–016: registration=false vẫn tạo USER 201 enabled/mustChangePassword, temporaryPassword chỉ ghi present/no-store; duplicate 409, invalid 400, USER 403, audit ADMIN_CREATE_USER, GET detail không trả credential. Phần còn lại hiện tại: Đủ residual local trước đó. Human sign-off riêng; không suy thành đã thử credential branch bị chặn hoặc mọi UI Admin. Tách riêng human sign-off, live, contract và kết quả tự động. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): E kiểm quyền actor ADMIN sau lock trước tạo user; AdminIntegrationTest18/18.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M13] Admin — Quản lý người dùng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
