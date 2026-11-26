# [BE] Xoá tài khoản user

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M13] Admin — Quản lý người dùng |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 05/11/2026 → 07/11/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Xoá tài khoản user` thuộc **[M13] Admin — Quản lý người dùng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

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

- **Thực hiện:** cùng EP-AD8/AM-4319, DELETE /api/admin/users/{id} ADMIN trả 204; thiếu ID 404 USER_NOT_FOUND, tự xóa 400 SELF_ACTION_FORBIDDEN, Admin hoạt động cuối 400 LAST_ADMIN; khóa DB. Revoke tất cả hộp thư có OAuth2 dù preferred APP_PASSWORD, best-effort; lỗi revoke có audit REVOKE_TOKEN_FAILED. Xóa liên kết và user, gỡ FK nhật ký/settings giữ bản ghi, audit ADMIN_DELETE_USER actor/target/count.
- **Mã nguồn:** `controller/AdminController.java#deleteUser`, `service/impl/AdminServiceImpl.java#deleteUser`, `repository/UserRepository.java#lockActiveAdminIds`, `repository/AuditLogRepository.java#detachUser`, `oauth2/OAuth2ServiceImpl.java#revokeToken`.
- **Test source:** `controller/AdminIntegrationTest.java` (testResetPasswordAndCascadeDeleteWithOAuthRevoke, testAdminSelfProtectionAndLastAdminProtection). Google revoke và MySQL thật còn cần kiểm tra.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

- **Bổ sung vòng G2 (sau F):** Versioned mailbox delete chống stale snapshot; AdminIntegrationTest 18/18. Không phải chứng minh invariant đúng hai Admin cuối hoặc Google revoke thật.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Admin delete theo quyền/target; không flag riêng để xóa user.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4332 (H refs trong hàng AM-4332, OBSERVED): H079 admin xóa id 5 fixture có OAuth 204; H080 detail 404 USER_NOT_FOUND, H081 token cũ 401; provider fixture revoke tổng 2. Retest bản cuối: [F029](../backend-final-observations.md) (OBSERVED): ADMIN DELETE chính mình 400 SELF_ACTION_FORBIDDEN.; [F030](../backend-final-observations.md) (OBSERVED): ADMIN DELETE ID không tồn tại 404 USER_NOT_FOUND.; [F031](../backend-final-observations.md) (OBSERVED): USER DELETE target 403 FORBIDDEN.; [F032](../backend-final-observations.md) (PARTIAL): /me ADMIN 200 vẫn role ADMIN, id1 sau self/foreign/missing guards. H079–081 delete target 204/detail404/token401, H062–065 target OAuth linked; provider revoke local được báo ở H snapshot.; [F091](../backend-final-observations.md) (OBSERVED): ADMIN detail user id4 sau delete 404 USER_NOT_FOUND.; [F092](../backend-final-observations.md) (OBSERVED): JWT cũ của target gọi /me 401 UNAUTHORIZED sau cascade.; [F094](../backend-final-observations.md) (PARTIAL): GET admin logs action 200 đúng 1 ADMIN_DELETE_USER actor admin id1, target id4, detail 2 liên kết Gmail; F088–090 target hai link và 204, F091/092 detail404/JWT401, F089/093 stats delta, F095 unrelated mailbox giữ nguyên; F029–032 self/missing/USER deny/admin retained. Prime báo manual provider 2 successful revokes tổng cộng cho unlink/cascade.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G081–091: cascade unlink với revoke failure thực được provider đếm; target 404, session cũ 401, owner khác còn mailbox, audit đúng. Phần còn lại hiện tại: Còn last-admin guard độc lập (không suy từ self guard); Google thật và human sign-off. Không thử xóa admin bootstrap để ép ca. Tách riêng human sign-off, live, contract và kết quả tự động. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): E delete cũng recheck ADMIN trong transaction; E008–022: hai tài khoản fixture được promote ADMIN, cả hai endpoint Admin 200; hai PUT demote chồng lấn (InnoDB lock-wait=1 E015) trả 200/403 vì kiểm tra actor dưới transaction lock. Actor mất role gọi Admin 403; restore cả hai USER, đúng 1 enabled ADMIN, bootstrap không bị sửa; 4 audit thành công, không audit giả. Đây là overlap native với 2 admin hy sinh và bootstrap vẫn tồn tại, không phải ca đúng 2 active Admin cuối. Còn last-admin độc lập và Google revoke thật. **Vòng G2:** Versioned mailbox delete chống stale snapshot; AdminIntegrationTest 18/18. Không phải chứng minh invariant đúng hai Admin cuối hoặc Google revoke thật. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4332-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4332-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M13] Admin — Quản lý người dùng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
