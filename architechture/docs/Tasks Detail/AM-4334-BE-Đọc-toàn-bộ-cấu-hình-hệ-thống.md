# [BE] Đọc toàn bộ cấu hình hệ thống

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M14] Admin — Cấu hình, Thống kê & Nhật ký |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 28/10/2026 → 30/10/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Đọc toàn bộ cấu hình hệ thống` thuộc **[M14] Admin — Cấu hình, Thống kê & Nhật ký** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-S1 GET /api/admin/settings`

- **Path:** `/api/admin/settings`
- **Method:** GET
- **Role:** ADMIN | **Category:** Admin - Settings

**Mô tả:** Toàn bộ cấu hình system_settings (9 khoá).

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/admin/settings
- JWT + ADMIN
- `systemSettingRepository.findAll()` → map `{key, value, description, updatedBy, updatedAt}`
- 200 array; sort by key

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** GET /api/admin/settings ADMIN 200 array sort key tăng dần. AdminSettingItem gồm key/value/description/updatedAt/updatedByUsername/updatedBy (alias), type/minValue/maxValue. MAX_GMAIL_PER_USER trả minValue=0 chỉ khi DB đang lưu 0 cũ, còn lại 1.
- **Khác biệt:** chỉ đúng whitelist 9 khóa và mặc định khi thiếu hàng thay trả mọi khóa DB (tránh lộ cấu hình ngoài phạm vi).
- **Mã nguồn:** `controller/AdminController.java#getSettings`, `service/impl/AdminServiceImpl.java#getAllSettings`, `dto/admin/AdminDtos.java`.
- **Test source:** `controller/AdminIntegrationTest.java#testSettingsValidationCacheEvictionAndAudit` (GET sort, PUT response updatedByUsername) và testRbac_Unauthenticated401AndRegularUser403. Kiểm tra alias updatedBy/null trên Postman.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Admin đọc setting, không Gmail hay audit mutation riêng.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ADMIN đọc settings; mapping flag/metadata. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Admin đọc setting, không Gmail hay audit mutation riêng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4334 (H refs trong hàng AM-4334, OBSERVED): H006 admin settings 200 đủ 9 key sắp theo key, type/range/description/update metadata; H007 USER 403, H122 không JWT 401; H115 updatedBy=admin và updatedAt mới. Phần còn lại: Scope §4 đã quan sát; human signoff riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): E E2E16/16/Newman653/653 kiểm contract hiện tại; không thêm manual settings.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M14] Admin — Cấu hình, Thống kê & Nhật ký**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
