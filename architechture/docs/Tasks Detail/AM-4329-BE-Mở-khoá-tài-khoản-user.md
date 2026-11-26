# [BE] Mở khoá tài khoản user

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
| Ước lượng | 0.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Mở khoá tài khoản user` thuộc **[M13] Admin — Quản lý người dùng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-AD5 PUT /api/admin/users/{id}/unlock`

- **Path:** `/api/admin/users/{id}/unlock`
- **Method:** PUT
- **Role:** ADMIN | **Category:** Admin - User Management

**Mô tả:** Mở khoá tài khoản (enabled=true).

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

PUT /api/admin/users/{id}/unlock
- `enabled=true` → save; log `ADMIN_UNLOCK_USER`; response `{id, enabled:true}`; 404 nếu không tồn tại

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** PUT /api/admin/users/{id}/unlock, ADMIN, enabled=true; audit ADMIN_UNLOCK_USER, 404 USER_NOT_FOUND, 200 AdminUserSummary có id/enabled=true. Cùng cơ chế khóa active-admin; token còn hạn có thể dùng lại sau mở khóa.
- **Mã nguồn:** `controller/AdminController.java#unlockUser`, `service/impl/AdminServiceImpl.java#unlockUser`, `security/JwtAuthenticationFilter.java`.
- **Test source:** `controller/AdminIntegrationTest.java#testLockUserImmediatelyInvalidatesJwt` (lock/reject/unlock/accept), RBAC test.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Mở khóa user theo role; không thao tác Gmail hay toggle riêng.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Mở khóa user theo role; không thao tác Gmail hay toggle riêng.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4329 (H refs trong hàng AM-4329, OBSERVED): H068 unlock 200 enabled=true; H069 session dùng lại được 200 đúng USER; H145 missing id 404 USER_NOT_FOUND; H149 ADMIN_UNLOCK_USER actor/target đúng. Retest bản cuối: [F023](../backend-final-observations.md) (OBSERVED): USER PUT unlock target 403 FORBIDDEN. H068–069 unlock 200 enabled=true/session 200, H145 missing 404, H149 ADMIN_UNLOCK_USER đúng actor/target.. Phần còn lại: Scope unlock local đã quan sát đủ. Human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): E unlock cũng recheck ADMIN sau lock; AdminIntegrationTest18/18, không thêm manual unlock.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M13] Admin — Quản lý người dùng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
