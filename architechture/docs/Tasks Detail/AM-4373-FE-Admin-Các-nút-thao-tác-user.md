# [FE-Admin] Các nút thao tác user

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M15] Màn hình Admin |
| Owner | TV6 |
| Platform | AdminWeb |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 10/11/2026 → 11/11/2026 |
| Ước lượng | 2.5h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[FE-Admin] Các nút thao tác user` thuộc **[M15] Màn hình Admin** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `AdminFrame` (hoặc `AdminPanel` trong MainFrame CardLayout) — chỉ khởi tạo nếu `userInfo.role=ADMIN`
- `JTabbedPane` 4 tab:
  - **Người dùng**: search field + filter role/enabled + JTable + nút [Tạo mới, Khoá/Mở khoá, Đổi role, Đặt lại MK, Xoá] — mỗi action confirm dialog
  - **Cấu hình**: form load EP-S1 → `JCheckBox` cho boolean flags, `JSpinner`/`JTextField` cho int, nút "Lưu" → EP-S2. Công tắc `ALLOW_REGISTRATION` đặt đầu tab + label "Số user hiện tại: X"
  - **Thống kê**: panel read-only các con số từ EP-ST1, nút "Làm mới"
  - **Nhật ký**: filter (userId/action/date range) + JTable EP-ST2 phân trang
- Mọi call API → `SwingWorker`; error → JOptionPane với `code`/`message`

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** AdminFrame create, lock/unlock, role, reset mật khẩu, delete có xác nhận; username 3–64 (chữ/số/_), họ tên/email không rỗng và tối đa 255 ký tự, email đúng định dạng (đồng bộ AdminCreateUserRequest BE); temp password, chống tự thao tác/last admin; refresh Users/Stats; ApiClient EP-AD1..AD8.
- **Test liên quan:** AdminModuleClientTest, AdminIntegrationTest, ClientLiveServerE2ETest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.

- **Bổ sung G4:** G4 single-flight user mutation, recheck selected DTO/ADMIN/session sau confirm; create pending không cancel/close; temp password dialog/copy chỉ session hiện hành, clear field lúc close. Không có reset/credential observation mới.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AdminModuleClientTest: 5/5 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Ownership của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ riêng admin user operations: không ownership Gmail theo caller; xóa user có Gmail vẫn phải scope target, revoke/cascade an toàn ở 4319/4332. F giữ: không có flag riêng cho admin create/lock/role/reset/delete; ALLOW_REGISTRATION cấm tự đăng ký, không tự suy ra admin create bị cấm. Role/last-admin guard không miễn.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Feature của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ riêng admin user operations: không ownership Gmail theo caller; xóa user có Gmail vẫn phải scope target, revoke/cascade an toàn ở 4319/4332. F giữ: không có flag riêng cho admin create/lock/role/reset/delete; ALLOW_REGISTRATION cấm tự đăng ký, không tự suy ra admin create bị cấm. Role/last-admin guard không miễn.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4373 (M24, BLOCKED): Chưa vào AdminFrame. Phần còn lại: Không create/detail/lock/unlock/role/reset/delete/temp-password dialog/last-admin UI. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — AdminFrame disable self lock/demotion/delete; không suy thành manual LAST_ADMIN/reset/dialog. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): E008–022: hai tài khoản fixture được promote ADMIN, cả hai endpoint Admin 200; hai PUT demote chồng lấn (InnoDB lock-wait=1 E015) trả 200/403 vì kiểm tra actor dưới transaction lock. Actor mất role gọi Admin 403; restore cả hai USER, đúng 1 enabled ADMIN, bootstrap không bị sửa; 4 audit thành công, không audit giả. Đây là overlap native với 2 admin hy sinh và bootstrap vẫn tồn tại, không phải ca đúng 2 active Admin cuối. Đây là HTTP, không Swing dialogs manual. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **G4:** G4 single-flight user mutation, recheck selected DTO/ADMIN/session sau confirm; create pending không cancel/close; temp password dialog/copy chỉ session hiện hành, clear field lúc close. Không có reset/credential observation mới. [AM-4373-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4373-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M15] Màn hình Admin**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
