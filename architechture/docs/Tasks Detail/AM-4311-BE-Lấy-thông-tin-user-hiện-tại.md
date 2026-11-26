# [BE] Lấy thông tin user hiện tại

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
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Lấy thông tin user hiện tại` thuộc **[M02] Xác thực tài khoản hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-A3 GET /api/auth/me`

- **Path:** `/api/auth/me`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** Authentication

**Mô tả:** Thông tin user hiện tại từ JWT (role, mustChangePassword) — Client gọi sau login để hiện khu Admin.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/auth/me
- JWT bắt buộc (JwtFilter)
- Trả `{id, username, fullName, email, role, mustChangePassword, createdAt}`
- KHÔNG trả password
- Client gọi sau login để: hiện tên user, biết role → hiện/ẩn khu Admin, biết mustChangePassword → bắt đổi
- Test: GET với Bearer → 200; không Bearer → 401; Bearer hết hạn → 401

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** GET /api/auth/me 200 đúng UserDto (gồm createdAt, mustChangePassword), không chứa hash; SecurityConfig yêu cầu JWT, filter tải user/role/enabled từ DB. Chặn token thiếu/sai/hết hạn hoặc user bị khóa 401; thêm 503 MAINTENANCE_MODE cho USER đã có JWT; Admin vẫn dùng được. JWT v2 đối chiếu username hiện tại với DB; legacy numeric-sub vẫn tra cứu bằng ID.
- **Mã nguồn:** `controller/AuthController.java#getCurrentUser`, `dto/UserDto.java`, `security/JwtAuthenticationFilter.java`, `security/JwtProvider.java`, `security/SecurityConfig.java`.
- **Test source:** `controller/AuthRegisterAndChangePasswordTest.java#testGetAuthMe`, `security/EndpointSecurityAuditTest.java`, `security/JwtAuthenticationFilterTest.java` (thiếu/sai token, locked, denylist, expiredCorrectlySignedJwtCannotAccessMe), `controller/AdminIntegrationTest.java#maintenanceRejectsExistingUserJwtButNotAdminOrPublic`. Test JWT hết hạn dùng đúng signing key, kiểm tra 401 và không lookup user.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

- **Bổ sung G3:** Session expiry callback G3; không thay expiry/forced-change manual.

- **Bổ sung G6:** G6: 4311-M.02 chuyển M/K theo bản ghi JSON đã scrubbed H005/H071/F026: GET /api/auth/me 200, đúng 7 key id, username, fullName, email, role, mustChangePassword, createdAt; không extra key, createdAt parse được, mustChangePassword boolean false. H029/H069/H133/F032 cũng đúng shape. Redactor giữ key, chỉ che value. Không truy cập raw/private manifest, không thực hiện login mới. 4311-M.03 vẫn H: expiry401 và forced-change /me thực.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AuthRegisterAndChangePasswordTest: 12/12 PASS; EndpointSecurityAuditTest: 3/3 PASS; JwtAuthenticationFilterTest: 9/9 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). /me chỉ đọc chính user, không Gmail hay audit mutation riêng.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). Cờ/giới hạn hoặc bảo trì được đối chiếu tại §4.1; server là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). /me chỉ đọc chính user, không Gmail hay audit mutation riêng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4311 (H refs trong hàng AM-4311, OBSERVED): H003–005 /me: thiếu/sai Bearer 401, đúng token 200 USER và identity; H029 request Postman 200 cho user khác; H071 role ADMIN cập nhật, H067/H069/H081 session khóa/mở/xóa. Phần còn lại: Token hết hạn, forced-change /me, đối chiếu không có password/hash trên toàn bộ payload; người nghiệm thu. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. **Vòng G3:** Session expiry callback G3; không thay expiry/forced-change manual. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4311-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4311-m). **G6:** G6: 4311-M.02 chuyển M/K theo bản ghi JSON đã scrubbed H005/H071/F026: GET /api/auth/me 200, đúng 7 key id, username, fullName, email, role, mustChangePassword, createdAt; không extra key, createdAt parse được, mustChangePassword boolean false. H029/H069/H133/F032 cũng đúng shape. Redactor giữ key, chỉ che value. Không truy cập raw/private manifest, không thực hiện login mới. 4311-M.03 vẫn H: expiry401 và forced-change /me thực.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M02] Xác thực tài khoản hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
