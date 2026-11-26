# [BE] Cờ cấu hình công khai

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M14] Admin — Cấu hình, Thống kê & Nhật ký |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 28/10/2026 → 29/10/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Cờ cấu hình công khai` thuộc **[M14] Admin — Cấu hình, Thống kê & Nhật ký** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-P1 GET /api/public/config`

- **Path:** `/api/public/config`
- **Method:** GET
- **Role:** PUBLIC | **Category:** Public

**Mô tả:** Cờ công khai cho Client quyết định ẩn/hiện nút trước đăng nhập: registrationEnabled, oauth2LinkEnabled, appPasswordLinkEnabled, maintenanceMode.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/public/config
- KHÔNG JWT — endpoint công khai cho Client chưa đăng nhập
- Trả `{registrationEnabled, oauth2LinkEnabled, appPasswordLinkEnabled, sendMailEnabled, maintenanceMode}` — chỉ cờ an toàn, KHÔNG trả giá trị nhạy cảm
- Đọc qua `SystemSettingService` (có cache)

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** GET /api/public/config public 200, trả registrationEnabled/oauth2LinkEnabled/appPasswordLinkEnabled/sendMailEnabled/maintenanceMode từ SystemSettingService cache; thêm maxGmailPerUser/maxAttachmentSizeMb/mailPageSize là giới hạn an toàn, không trả secret. Không cần JWT (kể cả đang bảo trì).
- **Mã nguồn:** `controller/PublicHealthController.java#getPublicConfig`, `dto/PublicConfigResponse.java`, `security/SecurityConfig.java`, `service/impl/SystemSettingServiceImpl.java`.
- **Test source:** `controller/AuthRegisterAndChangePasswordTest.java#testGetPublicConfig`, `controller/AdminIntegrationTest.java#testSettingsValidationCacheEvictionAndAudit` và maintenanceRejectsExistingUserJwtButNotAdminOrPublic.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AuthRegisterAndChangePasswordTest: 12/12 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Public config chỉ đọc các flag công khai; không mailbox hay audit mutation riêng.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). Public config phản ánh 5 cờ và 3 giới hạn công khai; không công khai cấu hình rate limit nội bộ hoặc secret. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Public config chỉ đọc các flag công khai; không mailbox hay audit mutation riêng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4333 (H refs trong hàng AM-4333, OBSERVED): H001–002 public config không JWT 200; H040 thấy registration/OAuth/send=false và pageSize=10; H044 restore true và pageSize=20; H116 App Password=false; H129 restore; H131 maintenance=true, H135 false. Phần còn lại: Scope §4 public flags đã quan sát; quota bổ sung nếu cần. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M14] Admin — Cấu hình, Thống kê & Nhật ký**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
