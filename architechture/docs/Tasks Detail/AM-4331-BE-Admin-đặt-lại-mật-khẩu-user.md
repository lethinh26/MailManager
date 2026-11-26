# [BE] Admin đặt lại mật khẩu user

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M13] Admin — Quản lý người dùng |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 03/11/2026 → 05/11/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Admin đặt lại mật khẩu user` thuộc **[M13] Admin — Quản lý người dùng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-AD7 PUT /api/admin/users/{id}/reset-password`

- **Path:** `/api/admin/users/{id}/reset-password`
- **Method:** PUT
- **Role:** ADMIN | **Category:** Admin - User Management

**Mô tả:** Đặt lại mật khẩu + must_change_password=true.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

PUT /api/admin/users/{id}/reset-password
- Body `{newPassword}` — validate ≥8
- `password=BCrypt.hashpw(newPassword)`, `must_change_password=true`
- Log `ADMIN_RESET_PASSWORD` `{targetUserId}` — KHÔNG log password
- Response `{message:"Đặt lại mật khẩu thành công"}`

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** PUT /api/admin/users/{id}/reset-password ADMIN; body tùy chọn {newPassword}: nếu có thì validate ít nhất 8 ký tự, tối đa 72 byte UTF-8, có chữ/số; nếu không body thì server tạo mật khẩu tạm 14 ký tự SecureRandom, BCrypt, mustChangePassword=true, audit ADMIN_RESET_PASSWORD không mật khẩu. 200 {userId,username,temporaryPassword,mustChangePassword,message} với Cache-Control no-store, 404 USER_NOT_FOUND.
- **Khác biệt bảo mật:** hỗ trợ body newPassword theo §4, trả message "Đặt lại mật khẩu thành công" và temporaryPassword=null, không echo mật khẩu; mật khẩu tạm mới sinh chỉ trả một lần nếu không body. Cả hai bắt đổi lần sử dụng tiếp theo. Không có API đọc lại mật khẩu. Không khẳng định JWT cũ bị thu hồi vĩnh viễn.
- **Mã nguồn:** `controller/AdminController.java#resetPassword`, `service/impl/AdminServiceImpl.java#resetPassword` và generateSecureTempPassword, `security/MustChangePasswordFilter.java`.
- **Test source:** `controller/AdminIntegrationTest.java#testResetPasswordAndCascadeDeleteWithOAuthRevoke` (no-store, BCrypt, audit không secret), `controller/AdminIntegrationTest.java#stalePrincipalCannotUndoAdminResetOrLock`, `controller/AdminIntegrationTest.java#legacyResetBodyRespectsUtf8LimitAndNeverEchoesSuppliedPassword`, `controller/AuthRegisterAndChangePasswordTest.java#testMustChangePasswordFilterBlocksSensitiveEndpoints`. Reset khóa và refresh User để tránh mất cập nhật.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AuthRegisterAndChangePasswordTest: 12/12 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Nhận newPassword hợp lệ theo body gốc, không echo; giữ tùy chọn sinh mật khẩu tạm nếu không truyền.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Reset mật khẩu user theo role; không Gmail hay toggle riêng.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Reset mật khẩu user theo role; không Gmail hay toggle riêng.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4331 (H refs trong hàng AM-4331, PARTIAL): H075 reset 200 no-store, mật khẩu tạm [present], mustChangePassword=true; H076 metadata forced-change; H077 login mật khẩu tạm 200; H078 mailbox 403 PASSWORD_CHANGE_REQUIRED. Phần còn lại: Mật khẩu cũ sai, explicit newPassword yếu/đúng, đổi mật khẩu và mở lại chức năng; audit không secret. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): E reset cũng recheck ADMIN trong transaction; không chạy manual reset/login đã bị chặn. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4331-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4331-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M13] Admin — Quản lý người dùng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
