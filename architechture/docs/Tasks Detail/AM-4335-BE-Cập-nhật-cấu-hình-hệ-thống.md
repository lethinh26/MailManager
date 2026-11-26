# [BE] Cập nhật cấu hình hệ thống

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M14] Admin — Cấu hình, Thống kê & Nhật ký |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 29/10/2026 → 01/11/2026 |
| Ước lượng | 3h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Cập nhật cấu hình hệ thống` thuộc **[M14] Admin — Cấu hình, Thống kê & Nhật ký** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-S2 PUT /api/admin/settings`

- **Path:** `/api/admin/settings`
- **Method:** PUT
- **Role:** ADMIN | **Category:** Admin - Settings

**Mô tả:** Cập nhật nhiều khoá; validate; cấm tắt cả hai cách liên kết; log ADMIN_CHANGE_SETTING.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

PUT /api/admin/settings
- JWT + ADMIN; body `{settings: {"KEY":"value", ...}}`
- Validate từng key: `ALLOW_*` là boolean string, `MAX_*`/`MAIL_PAGE_SIZE`/`LOGIN_MAX_FAILED_ATTEMPTS` là int positive
- **Ràng buộc quan trọng**: sau update, KHÔNG được `ALLOW_OAUTH2_LINK=false` && `ALLOW_APP_PASSWORD_LINK=false` cùng lúc → 400 VALIDATION_ERROR
- Update từng key, `updatedBy=currentUser.username`, `updatedAt=now`
- Log `ADMIN_CHANGE_SETTING` cho từng khoá đổi: `{key, oldValue, newValue}`
- Invalidate cache `SystemSettingService`
- Response 200: danh sách settings mới

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** PUT /api/admin/settings ADMIN, {settings:{key:value}}; whitelist/boolean/int range; 400 VALIDATION_ERROR cho khóa lạ, giá trị sai, cả hai link flag false; khóa DB hai hàng liên kết và đọc trạng thái persisted trước update. Ghi actor, timestamp, ADMIN_CHANGE_SETTING cho từng giá trị đổi (lọc alias SETTING_CHANGE cũng tìm được, không ghi dòng trùng), cache invalidate sau commit. 200 danh sách mới theo EP-S1.
- **Khác biệt:** MAX_GMAIL_PER_USER 1..50 (giữ 0 vô hạn nếu DB đã lưu 0; cho phép gửi lại 0 khi lưu thiết lập khác, từ chối đặt quota dương thành 0), MAX_ATTACHMENT_SIZE_MB 1..50, MAIL_PAGE_SIZE 10..100 (mặc định 20, giữ nguyên giá trị đã lưu), LOGIN_MAX_FAILED_ATTEMPTS 3..20; tất cả giá trị mới phải dương. Cache sau commit thay làm mới trong transaction tránh lộ giá trị rollback.
- **Mã nguồn:** `controller/AdminController.java#updateSettings`, `service/impl/AdminServiceImpl.java#updateSettings`, `repository/SystemSettingRepository.java#lockLinkSettings`, `service/impl/SystemSettingServiceImpl.java`.
- **Test source:** `controller/AdminIntegrationTest.java` (testSettingsValidationCacheEvictionAndAudit, simultaneousSettingUpdatesCannotDisableBothLinkMethods); `controller/AuthRegisterAndChangePasswordTest.java#rejectedLoginAuditPersistsAndRateLimitTracksSettings`. E16 đã xác minh lock cạnh tranh trên MySQL native qua API. legacyUnlimitedQuotaIsPreservedButNewQuotaMustBePositive kiểm tra giữ giá trị 0, chặn chuyển dương về 0 và minValue động.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.


- **Bằng chứng cập nhật:** E16 đồng thời hai admin request tắt hai phương thức: một 200, một 400 VALIDATION_ERROR, không thể tắt cả hai; restore.
## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AuthRegisterAndChangePasswordTest: 12/12 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Giá trị int mới dương, quota 0 chỉ giữ khi DB đã lưu legacy 0; validation/atomic/cache/audit. E16 trên native MySQL chỉ một trong hai cập nhật cạnh tranh tắt link flag được chấp nhận; Newman kiểm validation và restore.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Setting admin không truy cập hộp thư cụ thể; feature/audit vẫn bắt buộc.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). Whitelisting, range, ít nhất một link flag true và cache. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4335 (H refs trong hàng AM-4335, OBSERVED): H037 quota 0 từ chối 400, H038 tắt hai link flags 400; H039 PUT 200 đổi flags/pageSize, H040 public config mới; H041/H042 flag hiệu lực; H043–044 khôi phục. H113 body sai 400, H114 không đổi, H115 body đúng 200 metadata, H116 public flags, H119 ba ADMIN_CHANGE_SETTING chi tiết từng key; H128–139 restore/maintenance/cap và restore. Retest bản cuối: [F062](../backend-final-observations.md) (OBSERVED): F060 PUT cap1, F061 GET 413, F062 PUT cap25, F063 GET 200; H037–044/H113–119/H128–139 validation, metadata, setting audit, flag restore.. Phần còn lại: Scope local chính đủ; unknown key/range khác, atomic concurrency và DB commit nội bộ nếu yêu cầu thêm. Human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng E (automated): E updateSettings recheck ADMIN dưới khóa; E2E16/16/Newman653/653, không thêm manual settings.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M14] Admin — Cấu hình, Thống kê & Nhật ký**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
