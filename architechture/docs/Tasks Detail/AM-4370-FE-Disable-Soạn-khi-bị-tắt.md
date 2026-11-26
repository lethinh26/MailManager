# [FE] Disable Soạn khi bị tắt

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M11] Màn hình Soạn mail |
| Owner | TV5 |
| Platform | UserWeb |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 07/11/2026 → 08/11/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Disable Soạn khi bị tắt` thuộc **[M11] Màn hình Soạn mail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `ComposeDialog extends JDialog` modal
- From: `JComboBox` danh sách Gmail user đã liên kết (EP-G1) — không cho sửa
- To/Cc/Bcc: `JTextField` — allow nhiều email phân cách `,` hoặc `;`, validate mỗi cái
- Subject `JTextField`, Body `JTextArea` wrap + `JCheckBox` "Gửi HTML" toggle
- Nút "Đính kèm" → `JFileChooser` multi-select → `JList` file đã chọn, mỗi row: filename + size + nút xoá
- Validate client trước khi gửi: tổng size ≤ `MAX_ATTACHMENT_SIZE_MB`, địa chỉ `to` bắt buộc
- Nút "Gửi" → ApiClient.postMultipart → `SwingWorker` + progress bar — sau khi success: JOptionPane "Đã gửi" + đóng dialog
- Nếu `publicConfig.sendMailEnabled=false` → disable nút Gửi + label "Tính năng bị tắt bởi Admin"

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** MainFrame disable soạn/menu/shortcut/reply khi sendMailEnabled=false, ComposeDialog khóa nút Gửi và ghi lý do, apply config khi đang mở; refresh server trước gửi, BE ALLOW_SEND_MAIL vẫn là chốt.
- **Test liên quan:** SwingFeatureAcceptanceTest, MailServiceGreenMailIntegrationTest, AdminIntegrationTest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.

- **Bổ sung G5:** G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS; SwingFeatureAcceptanceTest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_SEND_MAIL. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: Giữ: enable/disable UI không mutation mail; successful send ở 4369 audit, thay setting ở 4374 audit.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4370 (M21, BLOCKED): Trước đổi flag, Compose/MainFrame gửi thật thành công và Send disable khi đang gửi. Prime tắt ALLOW_SEND_MAIL, nhưng console restart cuối không đăng nhập lại được để nhìn user controls. Phần còn lại: Không có actual dynamic disable observation của MainFrame/Compose/reply/shortcut; E14 tự động riêng, không dùng để đánh dấu manual. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — Compose không cập nhật từ public config cũ trong worker và bảo vệ session khi send; E2E tự động, không phải quan sát dynamic disable trực tiếp. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [Swing](../swing-supplemental-observations.md): Public config outage làm send disabled, refresh generation guard, giữ cảnh báo unknown khi flag đổi. Regression; chưa quan sát dynamic authenticated UI. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): E regression: public config null/error fail-closed, register/OAuth UI ẩn khi chưa biết flag hoặc maintenance, clear password/disable form khi mất config, worker kiểm session/dispose trước submit/callback. Client 60/60 gồm E2E16 và SwingFeatureAcceptanceTest13; không manual auth. E regression: recipient chips scroll riêng, giữ vùng input; commit recipient hợp lệ nguyên nhóm, tối đa 50. Compose giữ generation phiên, kiểm lại sau chooser/confirm; attachment regular/readable NOFOLLOW_LINKS, dedup/size; thiếu status SENT hoặc messageId không rỗng không được báo thành công, giữ draft/unknown và không auto resend. Không coi metadata check là TOCTOU-proof. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4370-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4370-m). **G5:** G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M11] Màn hình Soạn mail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
