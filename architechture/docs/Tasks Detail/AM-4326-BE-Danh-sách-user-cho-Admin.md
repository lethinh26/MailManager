# [BE] Danh sách user cho Admin

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

Hoàn thành `[BE] Danh sách user cho Admin` thuộc **[M13] Admin — Quản lý người dùng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-AD2 GET /api/admin/users`

- **Path:** `/api/admin/users`
- **Method:** GET
- **Role:** ADMIN | **Category:** Admin - User Management

**Mô tả:** Danh sách user: tìm username/email/họ tên, lọc role/enabled, phân trang, kèm gmailCount.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/admin/users?q=&role=&enabled=&page=&size=
- JWT + ADMIN
- `UserRepository.findAll(Specification, Pageable)`:
  - `q` → `username like %q% OR email like %q% OR fullName like %q%`
  - `role` → equal; `enabled` → equal
  - Sort: `createdAt DESC` mặc định
- Join count gmail per user (subquery `gmailAccountRepository.countByUserId`)
- Response 200 Page: `{content:[{id, username, fullName, email, role, enabled, gmailCount, createdAt}], page, size, totalElements, totalPages}`

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** GET /api/admin/users ADMIN, q (hoặc username alias) tìm case-insensitive trên username/fullName/email; role/enabled lọc, page mặc định 0, size 20 (tối đa 100), createdAt DESC mặc định; cho chọn sortBy/sortDir hợp lệ. 200 PageResponse với content metadata/gmailCount/mustChangePassword/updatedAt và page/size/totalElements/totalPages, không secret.
- **Khác biệt kỹ thuật:** JPQL + Pageable thay Specification; vẫn đáp ứng ngữ nghĩa lọc. Rà soát N/A: không yêu cầu actor ADMIN là owner của các Gmail; yêu cầu đếm theo đúng ID user đích và quyền ADMIN là áp dụng, kiểm tra bằng test danh sách/chi tiết.
- **Mã nguồn:** `controller/AdminController.java#listUsers`, `service/impl/AdminServiceImpl.java#listUsers`, `repository/UserRepository.java`, `dto/admin/AdminDtos.java`.
- **Test source:** `controller/AdminIntegrationTest.java#testListUsersFilteringAndUserDetailNoSecrets` và testRbac_Unauthenticated401AndRegularUser403.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). ADMIN role guard + gmailCount theo đúng target qua Newman N49/O24, không mở mail người khác; không còn N/A.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). List/filter metadata read-only; không flag hay audit mutation riêng, ownership được kiểm theo đúng target ADMIN.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). List/filter metadata read-only; không flag hay audit mutation riêng, ownership được kiểm theo đúng target ADMIN.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4326 (H refs trong hàng AM-4326, OBSERVED): H031 admin q/role/enabled/page/size trả 2 USER enabled, gmailCount 0/1, H034 USER 403; H074 filter tìm user mới. Retest bản cuối: [F033](../backend-final-observations.md) (OBSERVED): ADMIN list page0 size2, total4/2 pages, id4 createdAt 14:06:15 gmailCount2, id2 14:06:13 gmailCount0.; [F034](../backend-final-observations.md) (OBSERVED): Page1 size2, id3 14:06:13 gmailCount1 và id1 14:06:10 gmailCount0; 4 ID không trùng, date DESC và tie không mất dòng. H031 filter, H034 USER403, H074 list created.. Phần còn lại: Scope list/filter/paging local đã quan sát; sort khác và concurrent updates ngoài phạm vi chính, human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M13] Admin — Quản lý người dùng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
