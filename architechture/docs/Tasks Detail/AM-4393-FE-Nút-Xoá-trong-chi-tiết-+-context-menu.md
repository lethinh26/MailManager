# [FE] Nút Xoá trong chi tiết + context menu

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M16] Xoá mail (Trash semantics) |
| Owner | TV4 |
| Platform | UserWeb |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 10/11/2026 → 13/11/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Nút Xoá trong chi tiết + context menu` thuộc **[M16] Xoá mail (Trash semantics)** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- Trong `MailDetailPanel` (M09): nút "Xoá" → `JOptionPane.showConfirmDialog("Chuyển vào Thùng rác?")` → EP-M6 → refresh danh sách
- Trong `MailPanel` (JTable): right-click context menu → "Xoá" tương tự (xử lý cả multi-select nếu JTable hỗ trợ)
- Nếu đang ở folder Trash → confirm text "Xoá vĩnh viễn?"

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** MainFrame nút Xóa, menu chuột phải/đa chọn, phím Delete, xác nhận chuyển Trash hoặc xóa vĩnh viễn đúng folder, SwingWorker; API EP-M6 trả 204 mặc định; xác nhận permanent=true khi xóa trong Trash. Lỗi chắc chắn refresh danh sách; I/O/interruption/cancel hoặc HTTP 5xx (trừ MAINTENANCE_MODE) có thể là kết quả xóa chưa xác định: ApiException.isDeleteOutcomeUncertain → dừng lô, cảnh báo, reload folders, yêu cầu kiểm tra thư mục nguồn/Trash trước thao tác tiếp, không tự retry. CleanupService dừng lô với DELETE_UNCERTAIN nếu file đã lưu nhưng kết quả xóa chưa xác nhận. E2E 14 kiểm happy path detail→Trash→permanent, chưa kiểm uncertain hay context menu/đa chọn.
- **Test liên quan:** MailApiClientNewEndpointsTest, MailServiceGreenMailIntegrationTest; menu/xác nhận cần thao tác Swing tay.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.


- **Bằng chứng cập nhật:** Khi kết quả DELETE không xác định, dừng auto-clean kể cả đã đổi mailbox, không retry và đồng bộ timer sau cleanup.

- **Bổ sung G3:** MailboxRegressionTest 10/10 + AttachmentCleanupServiceTest 10/10: stop batch khi selection đổi, không retry uncertain, bảo toàn acknowledged count; cleanup identity/index invalid fail closed; cancel khi tải không DELETE. Modal đóng trong lúc chạy yêu cầu dừng; native confirmation còn thiếu.

- **Bổ sung G4:** G4 toolbar detail delete/cleanup chỉ đúng UID hiển thị, context/keyboard batch tách riêng; MailboxRegressionTest 11/11. Vẫn giữ permanent confirmation/UID-targeted/no retry, chưa native.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailServiceFailureRegressionTest: 22/22 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; AttachmentCleanupServiceTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS; MailApiClientNewEndpointsTest: 6/6 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE; không toggle delete riêng. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). MAIL_DELETE chuẩn; Client gọi API và E2E kiểm đúng 2 log.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4393 (M28, PARTIAL): Self-send marker: detail Xóa→confirm "Chuyển 1 thư ... vào Thùng rác?" No giữ đúng row; Yes → search INBOX 0, click lại account thấy Trash, chọn Trash đúng marker; detail Xóa→prompt **XÓA VĨNH VIỄN** → Yes, Trash 0. Sau restart direct row right-click → "Xóa thư đã chọn" → confirm No giữ mail seed. Ảnh M28-permanent-confirm. Phần còn lại: Không multi-select actual, Delete keyboard, permanent No, mất response/post-mutation/uncertain; không gây lỗi fixture chia sẻ. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng C: [Swing](../swing-supplemental-observations.md): 503 AUDIT_UNAVAILABLE là pre-mutation failure đã biết; FE không phân loại thành unknown. C029–030 không mutation, chưa manual UI. **Vòng G3:** MailboxRegressionTest 10/10 + AttachmentCleanupServiceTest 10/10: stop batch khi selection đổi, không retry uncertain, bảo toàn acknowledged count; cleanup identity/index invalid fail closed; cancel khi tải không DELETE. Modal đóng trong lúc chạy yêu cầu dừng; native confirmation còn thiếu. Không đổi checkbox. **G4:** G4 toolbar detail delete/cleanup chỉ đúng UID hiển thị, context/keyboard batch tách riêng; MailboxRegressionTest 11/11. Vẫn giữ permanent confirmation/UID-targeted/no retry, chưa native. [AM-4393-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4393-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M16] Xoá mail (Trash semantics)**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
