# [BE] Đổi role user

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M13] Admin — Quản lý người dùng |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 02/11/2026 → 04/11/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Đổi role user` thuộc **[M13] Admin — Quản lý người dùng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-AD6 PUT /api/admin/users/{id}/role`

- **Path:** `/api/admin/users/{id}/role`
- **Method:** PUT
- **Role:** ADMIN | **Category:** Admin - User Management

**Mô tả:** Đổi role USER<->ADMIN; chặn tự hạ mình / hạ Admin cuối.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

PUT /api/admin/users/{id}/role
- Body `{role: "ADMIN"|"USER"}`
- Chặn tự đổi role chính mình → 400
- Chặn hạ role của Admin cuối cùng → 400 "Phải còn ít nhất 1 Admin"
- Log `ADMIN_CHANGE_ROLE` `{targetUserId, oldRole, newRole}`
- Response `{id, role}`

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** PUT /api/admin/users/{id}/role ADMIN, role ADMIN|USER; tự đổi (kể cả ADMIN→ADMIN) 400 SELF_ACTION_FORBIDDEN, thiếu user 404 USER_NOT_FOUND, không cho hạ Admin hoạt động cuối với lock DB. Audit ADMIN_CHANGE_ROLE old/new/actor/target; 200 AdminUserSummary; JWT dùng role mới từ DB.
- **Khác biệt:** 400 LAST_ADMIN đúng §4, response mở rộng thay {id,role}; khóa danh sách active Admin trước đổi.
- **Mã nguồn:** `controller/AdminController.java#updateUserRole`, `service/impl/AdminServiceImpl.java#updateUserRole`, `repository/UserRepository.java#lockActiveAdminIds`.
- **Test source:** `controller/AdminIntegrationTest.java#testAdminSelfProtectionAndLastAdminProtection` (gồm self same role và LAST_ADMIN trực tiếp service); cạnh tranh lock cùng cơ chế được test ở simultaneousAdminLocksCannotDisableEveryAdmin; staleAdminPrincipalPasswordChangePreservesDemotion kiểm tra principal ADMIN cũ không thể ghi đè role USER khi đổi mật khẩu. Cạnh tranh hạ quyền/xóa trên MySQL thật chưa xác minh.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). LAST_ADMIN trả 400, không cho hạ admin cuối, recheck quyền trong transaction.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Đổi role theo role/last-admin guard; không Gmail hay toggle riêng.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Đổi role theo role/last-admin guard; không Gmail hay toggle riêng.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4330 (H refs trong hàng AM-4330, PARTIAL): H070 promote id 5 ADMIN 200; H071 /me của session hiện tại phản ánh ADMIN; H072 restore USER 200; H150 hai ADMIN_CHANGE_ROLE với old/new role. Retest bản cuối: [F024](../backend-final-observations.md) (OBSERVED): ADMIN gửi role INVALID 400 VALIDATION_ERROR; thông báo chỉ ADMIN/USER.; [F025](../backend-final-observations.md) (OBSERVED): USER đổi role target 403 FORBIDDEN.; [F028](../backend-final-observations.md) (PARTIAL): ADMIN tự demote 400 SELF_ACTION_FORBIDDEN; H070–072 promote/read-back/restore và H150 audit old/new role.. Phần còn lại: Còn last-admin guard độc lập/concurrency và quyền admin endpoint sau thay đổi role; không suy last-admin từ self guard. Human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): E008–022: hai tài khoản fixture được promote ADMIN, cả hai endpoint Admin 200; hai PUT demote chồng lấn (InnoDB lock-wait=1 E015) trả 200/403 vì kiểm tra actor dưới transaction lock. Actor mất role gọi Admin 403; restore cả hai USER, đúng 1 enabled ADMIN, bootstrap không bị sửa; 4 audit thành công, không audit giả. Đây là overlap native với 2 admin hy sinh và bootstrap vẫn tồn tại, không phải ca đúng 2 active Admin cuối. Còn last-admin guard độc lập đúng đặc tả, không ép qua bootstrap/fake principal. Phần còn lại hiện tại: Còn last-admin guard độc lập đúng điều kiện gốc; quyền sau đổi và overlap native đã có E008–022. Không đồng nhất race trên 3 enabled Admin hoặc self guard với race đúng 2 Admin cuối. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4330-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4330-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M13] Admin — Quản lý người dùng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
