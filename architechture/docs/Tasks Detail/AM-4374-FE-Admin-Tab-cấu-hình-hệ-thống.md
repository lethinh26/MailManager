# [FE-Admin] Tab cấu hình hệ thống

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M15] Màn hình Admin |
| Owner | TV6 |
| Platform | AdminWeb |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 11/11/2026 → 12/11/2026 |
| Ước lượng | 2h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[FE-Admin] Tab cấu hình hệ thống` thuộc **[M15] Màn hình Admin** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- Tab Cấu hình hệ thống của `AdminFrame` tải EP-S1 `GET /api/admin/settings`; chỉ ADMIN được thao tác.
- Checkbox cho boolean flags, spinner/ô số cho MAX_GMAIL_PER_USER, MAX_ATTACHMENT_SIZE_MB, MAIL_PAGE_SIZE, LOGIN_MAX_FAILED_ATTEMPTS; hiển thị số user hiện tại.
- Kiểm tra dữ liệu và ràng buộc phải còn ít nhất một phương thức liên kết Gmail; xác nhận trước khi bật Maintenance Mode.
- Nút Lưu gọi EP-S2 `PUT /api/admin/settings` trong SwingWorker; thông báo thành công/lỗi, cập nhật metadata và refresh cấu hình Client. Thay đổi phải thực sự được các consumer Backend/Frontend áp dụng.
- Đủ 9 keys: ALLOW_REGISTRATION, ALLOW_OAUTH2_LINK, ALLOW_APP_PASSWORD_LINK, ALLOW_SEND_MAIL, MAINTENANCE_MODE, MAX_GMAIL_PER_USER, MAX_ATTACHMENT_SIZE_MB, MAIL_PAGE_SIZE, LOGIN_MAX_FAILED_ATTEMPTS.
- Nội dung SystemSettingService bị sao chép nhầm trước đây thuộc AM-4345; yêu cầu cache và consumer BE vẫn giữ tại task đó.

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** AdminFrame settings 9 key: checkbox boolean, spinner integer, load EP-S1, validate và lưu EP-S2, xác nhận bật bảo trì, báo metadata cập nhật, số user; callback refresh MainFrame, backend áp dụng động.
- **Test liên quan:** AdminModuleClientTest, AdminIntegrationTest, ClientLiveServerE2ETest#test11_DynamicSettingsMaintenanceAndPersistedAudit.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** Chỉ bật nút lưu sau khi đọc settings thành công; khóa controls khi đang đọc/lưu, commit giá trị spinner trước validation. SwingFeatureAcceptanceTest#adminIgnoresOlderSearchAndDisablesSettingsUntilLoaded.
- **Quota và tương thích dữ liệu cũ:** MAX_GMAIL_PER_USER mới 1–50; spinner chỉ cho min=0 nếu setting hiện lưu 0 và metadata cho phép (0 cũ = unlimited). Lưu setting khác giữ nguyên 0; sau khi lưu quota dương không được quay về 0. Các range còn lại: MAX_ATTACHMENT_SIZE_MB 1–50, MAIL_PAGE_SIZE 10–100, LOGIN_MAX_FAILED_ATTEMPTS 3–20. E2E 15 kiểm spinner page size + flag send, không chứng minh boundary/legacy quota; cần M25 trên DB disposable riêng.

- **Bổ sung G4:** G4 save settings recheck current admin sau confirm/trước request/callback; không công nhận native maintenance/save.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AdminModuleClientTest: 5/5 PASS; ClientLiveServerE2ETest: 16/16 PASS; SwingFeatureAcceptanceTest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Ownership của task đã rà lại ở bảng N/A swing-acceptance-cases.md: Giữ: admin settings không mailbox-specific; F/A áp dụng, kiểm consumer và ADMIN_CHANGE_SETTING.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). Các setting boolean/int và public config refresh. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4374 (M25, BLOCKED): Prime thực hiện flag fixture từ session của prime, đã xác nhận config thay đổi; LoginFrame M06 phản ánh registration. Phần còn lại: Không phải thao tác tab Settings. Không quan sát 9 keys, range/quota/legacy 0, confirm maintenance, save metadata qua UI. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): E updateSettings recheck quyền và E2E16/16; không manual setting UI. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **G4:** G4 save settings recheck current admin sau confirm/trước request/callback; không công nhận native maintenance/save. [AM-4374-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4374-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M15] Màn hình Admin**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
