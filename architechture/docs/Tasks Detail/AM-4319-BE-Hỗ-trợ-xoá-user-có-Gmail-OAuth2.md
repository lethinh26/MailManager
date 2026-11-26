# [BE] Hỗ trợ xoá user có Gmail OAuth2

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M06] Liên kết Gmail bằng OAuth2 |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 05/11/2026 → 07/11/2026 |
| Ước lượng | 2h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Hỗ trợ xoá user có Gmail OAuth2` thuộc **[M06] Liên kết Gmail bằng OAuth2** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-AD8 DELETE /api/admin/users/{id}`

- **Path:** `/api/admin/users/{id}`
- **Method:** DELETE
- **Role:** ADMIN | **Category:** Admin - User Management

**Mô tả:** Xoá user + toàn bộ Gmail liên kết (revoke OAuth2 trước).

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

DELETE /api/admin/users/{id}
- Chặn tự xoá chính mình; chặn xoá Admin cuối cùng
- `gmailAccountRepository.findByUserId(id)` → với mỗi cái authType=OAUTH2 → `OAuth2Service.revoke` (best-effort)
- Xoá gmail records → xoá user (cascade hoặc tay)
- Log `ADMIN_DELETE_USER` `{targetUserId, username, gmailCount}`
- Response 204

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** DELETE /api/admin/users/{id}, ADMIN; chặn tự xóa 400 SELF_ACTION_FORBIDDEN, Admin hoạt động cuối 400 LAST_ADMIN, thiếu ID 404 USER_NOT_FOUND. Khóa DB trước kiểm tra; revoke best-effort mọi Gmail hasOAuth (cả APP_PASSWORD ưu tiên), ghi REVOKE_TOKEN_FAILED nếu lỗi; xóa Gmail/user, gỡ FK audit/settings nhưng giữ nhật ký. Audit ADMIN_DELETE_USER chứa actor/target/username/gmailCount. 204 không body.
- **Mã nguồn:** `controller/AdminController.java#deleteUser`, `service/impl/AdminServiceImpl.java#deleteUser`, `repository/UserRepository.java#lockActiveAdminIds`, `oauth2/OAuth2ServiceImpl.java#revokeToken`.
- **Test source:** `controller/AdminIntegrationTest.java` (testResetPasswordAndCascadeDeleteWithOAuthRevoke, testAdminSelfProtectionAndLastAdminProtection). Revoke Google thực tế và MySQL cascade/lock còn cần xác minh.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

- **Bổ sung vòng G2 (sau F):** V9 stale-delete regression, AdminIntegrationTest 18/18. Không có race đúng hai Admin cuối hoặc revoke Google live mới.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; OAuth2MockWebServerTest: 21/21 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Admin xóa target không phụ thuộc cờ link; revoke/cascade/audit bắt buộc.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4319 (H refs trong hàng AM-4319, PARTIAL): H062–065 tạo OAuth account id 5 và admin detail có 1 mailbox; H079 xóa user 204, H080 detail 404, H081 token cũ 401; tổng revoke fixture 2. Retest bản cuối: [F085](../backend-final-observations.md) (OBSERVED): Owner authorize-url 200 no-store, URL/poll token chỉ ghi [present]; chuẩn bị target cascade.; [F086](../backend-final-observations.md) (OBSERVED): Callback loopback HTML 200 liên kết thành công, 6882 bytes, no-store.; [F087](../backend-final-observations.md) (OBSERVED): Poll 200 SUCCESS CREATED accountId=4 (OAuth mailbox mới).; [F088](../backend-final-observations.md) (OBSERVED): ADMIN detail user id4 200, gmailCount=2, linked id2 APP_PASSWORD và id4 OAUTH2 ACTIVE.; [F089](../backend-final-observations.md) (OBSERVED): Stats trước cascade: 4 users/4 active/1 admin, 3 Gmail = 2 App Password + 1 OAuth.; [F090](../backend-final-observations.md) (OBSERVED): ADMIN DELETE đúng target user id4 hai mailbox 204, body0.; [F093](../backend-final-observations.md) (OBSERVED): Stats sau cascade: 3 users/3 active/1 admin, 1 Gmail = 1 App Password + 0 OAuth; giảm đúng một user và hai liên kết so F089, admin count giữ nguyên.; [F095](../backend-final-observations.md) (PARTIAL): User ui_other GET list 200 vẫn duy nhất id1 APP_PASSWORD ACTIVE; F089–093 user/mailbox counts giảm chính xác, F094 audit target id4/two links, F029–032 guards. Prime báo manual provider 2 successful revokes tổng cộng cho unlink/cascade.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G081–091: cascade OAuth target, provider reject revoke, delete 204, detail 404 và session cũ 401; unrelated owner nguyên vẹn; audit đúng. Phần còn lại hiện tại: Còn last-admin guard độc lập và Google revoke thật; không ép bằng cách xóa/khóa/hạ quyền admin bootstrap. Tách riêng human sign-off, live, contract và kết quả tự động. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): E thêm kiểm actor ADMIN sau lock transaction cho cascade/delete; E008–022: hai tài khoản fixture được promote ADMIN, cả hai endpoint Admin 200; hai PUT demote chồng lấn (InnoDB lock-wait=1 E015) trả 200/403 vì kiểm tra actor dưới transaction lock. Actor mất role gọi Admin 403; restore cả hai USER, đúng 1 enabled ADMIN, bootstrap không bị sửa; 4 audit thành công, không audit giả. Đây là overlap native với 2 admin hy sinh và bootstrap vẫn tồn tại, không phải ca đúng 2 active Admin cuối. Revoke Google thật và last-admin độc lập còn thiếu. **Vòng G2:** V9 stale-delete regression, AdminIntegrationTest 18/18. Không có race đúng hai Admin cuối hoặc revoke Google live mới. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4319-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4319-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M06] Liên kết Gmail bằng OAuth2**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
