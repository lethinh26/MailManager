# [BE] Truy vấn audit log

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M14] Admin — Cấu hình, Thống kê & Nhật ký |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 03/11/2026 → 05/11/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Truy vấn audit log` thuộc **[M14] Admin — Cấu hình, Thống kê & Nhật ký** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-ST2 GET /api/admin/logs`

- **Path:** `/api/admin/logs`
- **Method:** GET
- **Role:** ADMIN | **Category:** Admin - Stats & Logs

**Mô tả:** Audit log lọc theo user/action/ngày, phân trang; không chứa bí mật.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/admin/logs?userId=&action=&from=&to=&page=&size=
- JWT + ADMIN
- `auditLogRepository.findAll(Specification, Pageable)` filter: userId, action contains, createdAt between
- Join user để lấy username
- Sort `createdAt DESC`
- Response Page `{content:[{id, userId, username, action, detail, ipAddress, createdAt}]}`

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** GET /api/admin/logs ADMIN, page/size, userId/username/action/from/to, action chứa chuỗi con literal không phân biệt hoa thường (LOCATE) và alias chính xác LOGIN_SUCCESS→LOGIN, SETTING_CHANGE↔ADMIN_CHANGE_SETTING, MAIL_DELETE↔DELETE_MAIL, OAUTH2_CALLBACK↔action OAuth2 cũ theo detail; mỗi dòng chỉ xuất hiện một lần, date bounds inclusive; sort createdAt DESC/id DESC. 200 PageResponse, item đúng id/userId/username/action/detail/ipAddress/createdAt.
- **Khác biệt:** JPQL LEFT JOIN thay Specification; có username filter và CSV export bảo vệ formula injection. User đã xóa vẫn giữ audit nhưng FK userId/username null; không khôi phục username lịch sử đã detach.
- **Mã nguồn:** `controller/AdminController.java#getLogs`, `service/impl/AdminServiceImpl.java#searchLogs`, `repository/AuditLogRepository.java#searchLogs`, `dto/admin/AdminDtos.java`.
- **Test source:** `controller/AdminIntegrationTest.java#testStatsLogsAndCsvInjectionProtection` (action chuỗi con, username, CSV). `controller/AdminIntegrationTest.java#auditAliasesFindOldAndNewRowsWithoutDuplicatingEvents`. Kiểm tra range ngày/biên page trên Postman.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Truy vấn/xuất audit read-only theo ADMIN; không Gmail/flag/audit đọc riêng.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Truy vấn/xuất audit read-only theo ADMIN; không Gmail/flag/audit đọc riêng.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Truy vấn/xuất audit read-only theo ADMIN; không Gmail/flag/audit đọc riêng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4337 (H refs trong hàng AM-4337, PARTIAL): H092 alias DELETE_MAIL tìm 2 MAIL_DELETE, page 0 size 1; H097 page 1 có dòng còn lại; H095 USER 403; H094 export Accept JSON 500, H096 đúng media 200 text/csv, disposition audit-logs.csv, 323 byte, header CSV. H098 username/action filter; H127 flag audit; H143 date filter rỗng 200. Retest bản cuối: [F015](../backend-final-observations.md) (OBSERVED): GET logs/export Accept sai 406 JSON NOT_ACCEPTABLE, path đúng; sửa H094 500.; [F016](../backend-final-observations.md) (PARTIAL): Accept text/csv 200 text/csv;charset=UTF-8, attachment audit-logs.csv, Content-Length=243, 243 bytes, 3 lines và header ID/UserID/Username/Action/Detail/IPAddress/CreatedAt. H092/097 có hai trang, H095 USER 403, H098 lọc username/action, H143 date range rỗng.; [F071](../backend-final-observations.md) (OBSERVED): GET admin logs với userId/action/from/to/page/size, page0 size1 total2/pages2, row id15 actor id4 MAIL_DELETE, createdAt 14:10:26, đúng Trash/permanent=true.; [F072](../backend-final-observations.md) (OBSERVED): Page1 size1 cùng filter, row id14 cùng actor/action, createdAt 14:10:10, INBOX/permanent=false; date DESC, không trùng.; [F073](../backend-final-observations.md) (OBSERVED): Export cùng userId/action/from/to 200 text/csv;charset=UTF-8, audit-logs.csv, 323 bytes/4 lines, header đúng. F015 Accept sai406, F016 Accept đúng200; H095 USER403, H143 date range rỗng.. Phần còn lại: Scope literal GET logs filter userId/action/date-positive, paging/sort và quyền local đủ; CSV summary không chứa row content nên chưa đối chiếu từng ô hoặc CSV injection, human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M14] Admin — Cấu hình, Thống kê & Nhật ký**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
