# [BE] Thống kê tổng quan

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M14] Admin — Cấu hình, Thống kê & Nhật ký |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 02/11/2026 → 04/11/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Thống kê tổng quan` thuộc **[M14] Admin — Cấu hình, Thống kê & Nhật ký** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-ST1 GET /api/admin/stats`

- **Path:** `/api/admin/stats`
- **Method:** GET
- **Role:** ADMIN | **Category:** Admin - Stats & Logs

**Mô tả:** Thống kê tổng quan: user, Gmail theo authType, đăng ký hôm nay/7 ngày.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/admin/stats
- JWT + ADMIN
- Query: `totalUsers, activeUsers(enabled=true), lockedUsers(enabled=false), totalAdmins(role=ADMIN), totalGmailAccounts, gmailByAppPassword(count where authType=APP_PASSWORD), gmailByOAuth2, registrationsToday(count users.created_at today), registrationsLast7Days`
- Response 200 object

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** GET /api/admin/stats ADMIN, 200 object đủ chỉ số §4; thêm gmailNeedsReauth, registrationsLast30Days, failedLoginsLast24h, mailsSentLast7DaysByDay (7 điểm, ngày không log count=0).
- **Ngữ nghĩa:** authType là phương thức ưu tiên, hộp thư đa phương thức tính một lần; totalAdmins gồm cả bị khóa. Registration tính theo ngày lịch máy chủ; failedLoginsLast24h đếm LOGIN_FAILED đã lưu, không bao gồm mọi lỗi OAuth2/rate limit. Rà soát N/A: thống kê toàn hệ thống chỉ trả aggregate cho ADMIN; không áp dụng ownership từng hộp thư, nhưng RBAC bắt buộc.
- **Mã nguồn:** `controller/AdminController.java#getStats`, `service/impl/AdminServiceImpl.java#getStats`, `dto/admin/AdminDtos.java`, `repository/UserRepository.java`, `repository/GmailAccountRepository.java`, `repository/AuditLogRepository.java`.
- **Test source:** `controller/AdminIntegrationTest.java#testStatsLogsAndCsvInjectionProtection` (một số count/7 ngày); `controller/AuthRegisterAndChangePasswordTest.java#rejectedLoginAuditPersistsAndRateLimitTracksSettings`. Test số liệu mọi trường với DB seed thực tế còn cần xác minh.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AuthRegisterAndChangePasswordTest: 12/12 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Stats aggregate theo quyền ADMIN, không mở mailbox từng owner; không flag/audit đọc riêng.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Stats aggregate theo quyền ADMIN, không mở mailbox từng owner; không flag/audit đọc riêng.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Stats aggregate theo quyền ADMIN, không mở mailbox từng owner; không flag/audit đọc riêng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4336 (H refs trong hàng AM-4336, OBSERVED): H035 stats 200: 6 users đều active, 0 locked, 1 admin, 3 Gmail, OAuth=0; gmailByAppPassword bị sanitizer thay [present] ở ca đầu; H146 USER 403, H147 stats 200 có AppPassword=3, đủ trường §4, 6 active+0 locked=6, 3 AppPassword+0 OAuth=3. Retest bản cuối: [F093](../backend-final-observations.md) (OBSERVED): F089→F093 stats 4→3 users, active 4→3, admin=1, Gmail 3→1, App Password 2→1, OAuth 1→0; F088/090/094 chứng minh target hai link đã xóa. H146 USER403/H147 counters.. Phần còn lại: Scope KPI local đủ, DB count độc lập ngoài response, human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M14] Admin — Cấu hình, Thống kê & Nhật ký**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
