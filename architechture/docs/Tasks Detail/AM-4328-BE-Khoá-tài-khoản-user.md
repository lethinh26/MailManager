# [BE] Khoá tài khoản user

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M13] Admin — Quản lý người dùng |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 30/10/2026 → 01/11/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Khoá tài khoản user` thuộc **[M13] Admin — Quản lý người dùng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-AD4 PUT /api/admin/users/{id}/lock`

- **Path:** `/api/admin/users/{id}/lock`
- **Method:** PUT
- **Role:** ADMIN | **Category:** Admin - User Management

**Mô tả:** Khoá tài khoản (enabled=false); chặn JWT cũ ở request sau; log ADMIN_LOCK_USER.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

PUT /api/admin/users/{id}/lock
- JWT + ADMIN; findById → 404
- Chặn: id == currentUser.id → 400 "Không thể tự khoá chính mình"
- Chặn: `user.role=ADMIN` && `userRepository.countByRoleAndEnabledTrue(ADMIN) <= 1` → 400 "Không thể khoá Admin cuối cùng"
- `enabled=false` → save; JWT cũ của user sẽ fail ở JwtFilter khi check `enabled` lần sau
- Log `ADMIN_LOCK_USER` `{targetUserId, username}`
- Response `{id, enabled:false}`

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** PUT /api/admin/users/{id}/lock, ADMIN; 404 USER_NOT_FOUND; tự khóa 400 SELF_ACTION_FORBIDDEN; transaction FOR UPDATE trên ID Admin hoạt động trước kiểm tra để tránh hai request khóa toàn bộ; enabled=false, audit ADMIN_LOCK_USER, JWT của user bị từ chối 401 ở request tiếp theo.
- **Khác biệt:** 400 LAST_ADMIN đúng §4 vì xung đột trạng thái; trả 200 AdminUserSummary (gồm id/enabled). Token cũ chưa bị thu hồi vĩnh viễn và có thể dùng khi mở khóa.
- **Mã nguồn:** `controller/AdminController.java#lockUser`, `service/impl/AdminServiceImpl.java#lockUser`, `repository/UserRepository.java#lockActiveAdminIds`, `security/JwtAuthenticationFilter.java`.
- **Test source:** `controller/AdminIntegrationTest.java` (testAdminSelfProtectionAndLastAdminProtection, simultaneousAdminLocksCannotDisableEveryAdmin, testLockUserImmediatelyInvalidatesJwt, stalePrincipalCannotUndoAdminResetOrLock). Ghi User khóa và refresh hàng hiện tại qua `repository/UserLockRepositoryImpl.java`; MySQL thật chưa xác minh cạnh tranh.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). LAST_ADMIN trả 400, giữ khóa giao dịch và bảo vệ admin cuối.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Khóa user theo role/last-admin guard; không thao tác Gmail hay toggle riêng.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Khóa user theo role/last-admin guard; không thao tác Gmail hay toggle riêng.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4328 (H refs trong hàng AM-4328, PARTIAL): H066 lock 200 enabled=false; H067 session trước đó trả 401; H148 ADMIN_LOCK_USER actor và target đúng. Retest bản cuối: [F021](../backend-final-observations.md) (OBSERVED): ADMIN PUT lock ID không tồn tại 404 USER_NOT_FOUND.; [F022](../backend-final-observations.md) (OBSERVED): USER PUT lock target 403 FORBIDDEN.; [F026](../backend-final-observations.md) (OBSERVED): ADMIN /me 200 id1, role ADMIN, mustChangePassword=false.; [F027](../backend-final-observations.md) (PARTIAL): ADMIN tự khóa id1 400 SELF_ACTION_FORBIDDEN; F032 /me vẫn 200 ADMIN. H066 lock target thành công, H067 JWT target 401, H148 audit.. Phần còn lại: Main flow + missing/role/self guards local đã quan sát; last-enabled-ADMIN guard độc lập không chứng minh được từ self guard (sole active admin cũng chính caller). Còn login account locked và kiểm concurrency an toàn; không khóa admin bootstrap để ép ca. Human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): E AdminIntegrationTest18/18 có overlap H2 và recheck actor; E008–022: hai tài khoản fixture được promote ADMIN, cả hai endpoint Admin 200; hai PUT demote chồng lấn (InnoDB lock-wait=1 E015) trả 200/403 vì kiểm tra actor dưới transaction lock. Actor mất role gọi Admin 403; restore cả hai USER, đúng 1 enabled ADMIN, bootstrap không bị sửa; 4 audit thành công, không audit giả. Đây là overlap native với 2 admin hy sinh và bootstrap vẫn tồn tại, không phải ca đúng 2 active Admin cuối. Ca locked-login và last-enabled-admin lock độc lập chưa có manual. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4328-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4328-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M13] Admin — Quản lý người dùng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
