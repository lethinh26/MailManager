# [FE-Admin] Tab danh sách user

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M15] Màn hình Admin |
| Owner | TV6 |
| Platform | AdminWeb |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 09/11/2026 → 10/11/2026 |
| Ước lượng | 2.5h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[FE-Admin] Tab danh sách user` thuộc **[M15] Màn hình Admin** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `AdminFrame` (hoặc `AdminPanel` trong MainFrame CardLayout) — chỉ khởi tạo nếu `userInfo.role=ADMIN`
- `JTabbedPane` 4 tab:
  - **Người dùng**: search field + filter role/enabled + JTable + nút [Tạo mới, Khoá/Mở khoá, Đổi role, Đặt lại MK, Xoá] — mỗi action confirm dialog
  - **Cấu hình**: form load EP-S1 → `JCheckBox` cho boolean flags, `JSpinner`/`JTextField` cho int, nút "Lưu" → EP-S2. Công tắc `ALLOW_REGISTRATION` đặt đầu tab + label "Số user hiện tại: X"
  - **Thống kê**: panel read-only các con số từ EP-ST1, nút "Làm mới"
  - **Nhật ký**: filter (userId/action/date range) + JTable EP-ST2 phân trang
- Mọi call API → `SwingWorker`; error → JOptionPane với `code`/`message`

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** AdminFrame loadUsers với search username/email, role, enabled, sort và paging; table không sửa trực tiếp; EP-AD1; xử lý 401/403.
- **Test liên quan:** AdminModuleClientTest, AdminIntegrationTest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** SwingFeatureAcceptanceTest#adminIgnoresOlderSearchAndDisablesSettingsUntilLoaded kiểm request tìm kiếm chậm không ghi đè kết quả mới; điều chỉnh page khi dữ liệu giảm.

- **Bổ sung G4:** G4 read workers guard generation/disposed; test late user rows sau dispose không cập nhật model. Native filter/paging/refresh vẫn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 2 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AdminModuleClientTest: 5/5 PASS; ClientLiveServerE2ETest: 16/16 PASS; SwingFeatureAcceptanceTest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Ownership của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ: admin list users theo role, không đọc mailbox từng owner; F giữ: không dedicated flag cho danh sách; A giữ: list/search users read-only. Các action khác của §4 lặp ở 4373/4374.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Feature của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ: admin list users theo role, không đọc mailbox từng owner; F giữ: không dedicated flag cho danh sách; A giữ: list/search users read-only. Các action khác của §4 lặp ở 4373/4374.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ: admin list users theo role, không đọc mailbox từng owner; F giữ: không dedicated flag cho danh sách; A giữ: list/search users read-only. Các action khác của §4 lặp ở 4373/4374.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4372 (M23, BLOCKED): Chưa vào tab Users. Phần còn lại: Search/filter/sort/paging/selection đều chưa thực hiện trên UI. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — AdminFrame reset page theo filter user/log đổi, disable self actions; regression adminPagingRestartsWhenUserOrLogCriteriaChanges. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. **G4:** G4 read workers guard generation/disposed; test late user rows sau dispose không cập nhật model. Native filter/paging/refresh vẫn thiếu. [AM-4372-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4372-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M15] Màn hình Admin**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
