# [FE-Admin] Tab nhật ký

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M15] Màn hình Admin |
| Owner | TV6 |
| Platform | AdminWeb |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 13/11/2026 → 15/11/2026 |
| Ước lượng | 1.5h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[FE-Admin] Tab nhật ký` thuộc **[M15] Màn hình Admin** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `AdminFrame` (hoặc `AdminPanel` trong MainFrame CardLayout) — chỉ khởi tạo nếu `userInfo.role=ADMIN`
- `JTabbedPane` 4 tab:
  - **Người dùng**: search field + filter role/enabled + JTable + nút [Tạo mới, Khoá/Mở khoá, Đổi role, Đặt lại MK, Xoá] — mỗi action confirm dialog
  - **Cấu hình**: form load EP-S1 → `JCheckBox` cho boolean flags, `JSpinner`/`JTextField` cho int, nút "Lưu" → EP-S2. Công tắc `ALLOW_REGISTRATION` đặt đầu tab + label "Số user hiện tại: X"
  - **Thống kê**: panel read-only các con số từ EP-ST1, nút "Làm mới"
  - **Nhật ký**: filter (userId/action/date range) + JTable EP-ST2 phân trang
- Mọi call API → `SwingWorker`; error → JOptionPane với `code`/`message`

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** AdminFrame logs lọc userId dương/username/action/from/to; paging và request generation, export CSV cùng filter, ApiClient EP-ST2; backend lọc action theo substring.
- **Test liên quan:** AdminModuleClientTest, AdminIntegrationTest, ClientLiveServerE2ETest#test11_DynamicSettingsMaintenanceAndPersistedAudit.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** Bộ lọc audit dùng LOGIN, MAIL_DELETE, OAUTH2_CALLBACK; backend tương thích alias lịch sử, không nhân đôi bản ghi.

- **Bổ sung G4:** G4 logs read worker kiểm generation/disposed, giữ CSV guard cũ; native filter/chooser vẫn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 2 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AdminModuleClientTest: 5/5 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Ownership của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ: audit admin read theo role không phải Gmail ownership; F giữ: không log-view flag riêng; A giữ: đọc/xuất audit không yêu cầu tự tạo mutation audit, nhưng phải chứng minh lọc đúng action/alias/không leak.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Feature của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ: audit admin read theo role không phải Gmail ownership; F giữ: không log-view flag riêng; A giữ: đọc/xuất audit không yêu cầu tự tạo mutation audit, nhưng phải chứng minh lọc đúng action/alias/không leak.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ: audit admin read theo role không phải Gmail ownership; F giữ: không log-view flag riêng; A giữ: đọc/xuất audit không yêu cầu tự tạo mutation audit, nhưng phải chứng minh lọc đúng action/alias/không leak.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4376 (M27, BLOCKED): Chưa vào Logs tab. Phần còn lại: Không filter/paging/export CSV/action aliases qua UI. Prime HTTP audit separate evidence. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — AdminFrame reset/clamp page logs, xóa list stale khi lỗi, tạo thư mục chooser CSV đã cấu hình và bỏ callback export sau dispose; regression paging. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): V8 có mailEventId/mailActorId additive trong audit response, CSV giữ cũ; không thêm thao tác UI. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **Vòng F:** [Swing vòng F (automated)](../swing-supplemental-observations.md): CSV export snapshot filter trước chooser; single-flight, reject existing file thay vì overwrite, chỉ text/csv, temp file và move không replace sau guard session/generation/role/disposed; xóa temp, không publish stale response. AdminModuleClientTest5/5: existing file không request/không đổi, response hết guard không tạo target/part. Không chứng minh native chooser/viewer, role-loss manual; không thu hồi file đã export hợp lệ trước logout. Không thay trạng thái checkbox. **G4:** G4 logs read worker kiểm generation/disposed, giữ CSV guard cũ; native filter/chooser vẫn thiếu. [AM-4376-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4376-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M15] Màn hình Admin**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
