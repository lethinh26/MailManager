# [BE] User tự đổi mật khẩu

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M02] Xác thực tài khoản hệ thống |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 29/10/2026 → 31/10/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] User tự đổi mật khẩu` thuộc **[M02] Xác thực tài khoản hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-A4 POST /api/auth/change-password`

- **Path:** `/api/auth/change-password`
- **Method:** POST
- **Role:** AUTHENTICATED | **Category:** Authentication

**Mô tả:** User tự đổi mật khẩu {currentPassword,newPassword}; xoá cờ must_change_password.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

POST /api/auth/change-password
- JWT bắt buộc; user hiện tại từ token
- Body `{currentPassword, newPassword}`
- Verify `BCrypt.matches(currentPassword, user.password)` → sai: 400 `VALIDATION_ERROR` message "Mật khẩu hiện tại sai"
- `newPassword` ≥8 ký tự, không trùng currentPassword → 400 VALIDATION_ERROR
- Update hash + `must_change_password=false`
- Log `CHANGE_PASSWORD`
- Response 200 `{message:"Đổi mật khẩu thành công"}`

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** POST /api/auth/change-password dùng ID của user xác thực để khóa và refresh hàng User hiện tại từ DB; từ chối user đã khóa/xóa, kiểm tra lại bảo trì và hash hiện tại. BCrypt, chính sách mới ít nhất 8 ký tự, tối đa 72 byte UTF-8, có chữ và số, không trùng; lưu hash, bỏ cờ bắt đổi, audit CHANGE_PASSWORD. Sai mật khẩu hiện tại có CHANGE_PASSWORD_FAILED lưu được khi từ chối; rate limit theo user ID. Không merge principal cũ đè trạng thái role/enabled/password mới.
- **Khác biệt:** 400 VALIDATION_ERROR khi mật khẩu hiện tại sai, mật khẩu mới trùng/yếu hoặc vượt giới hạn BCrypt; trả 200 {message,accessToken,tokenType,expiresIn,user}, no-store, để Client cập nhật phiên. Không khẳng định JWT cũ bị thu hồi.
- **Mã nguồn:** `controller/AuthController.java#changePassword`, `service/impl/AuthServiceImpl.java#changePassword`, `dto/ChangePasswordResponse.java`, `security/MustChangePasswordFilter.java`.
- **Test source:** `controller/AuthRegisterAndChangePasswordTest.java` (testChangePasswordSuccess, testChangePasswordWrongCurrent, testMustChangePasswordFilterBlocksSensitiveEndpoints, utf8BcryptLimitAppliesToRegisterLoginAndChangePassword), `controller/AdminIntegrationTest.java` (stalePrincipalCannotUndoAdminResetOrLock, staleAdminPrincipalPasswordChangePreservesDemotion); khóa/reload tại `repository/UserLockRepositoryImpl.java`.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AuthRegisterAndChangePasswordTest: 12/12 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). 400 VALIDATION_ERROR cho mật khẩu sai/yếu/trùng; 200 có message và JWT mới tương thích Client; không khẳng định thu hồi JWT cũ.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Đổi mật khẩu user, không mở hộp thư.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4312 (H refs trong hàng AM-4312, NOT OBSERVED): H078 chứng minh forced-change gate 403; chưa thấy request change-password trong snapshot. Phần còn lại: Đổi mật khẩu đúng/sai/trùng, kiểm login mật khẩu cũ thất bại/mới thành công và session theo contract. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4312-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4312-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M02] Xác thực tài khoản hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
