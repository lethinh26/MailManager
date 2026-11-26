# [FE] Chọn file đính kèm

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M11] Màn hình Soạn mail |
| Owner | TV5 |
| Platform | UserWeb |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 06/11/2026 → 07/11/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Chọn file đính kèm` thuộc **[M11] Màn hình Soạn mail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

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

- **Code và hành vi:** ComposeDialog chọn nhiều file/kéo thả, chips filename/size/xóa, tổng kích thước theo MAX_ATTACHMENT_SIZE_MB; xác minh file tồn tại/đọc được và giới hạn trước upload (kể cả file thay đổi), chấp nhận file 0 byte.
- **Test liên quan:** AcceptanceRegressionTest#composeRejectsInvalidRecipientsOversizedBodyAndChangedFiles, MailApiClientNewEndpointsTest, MailMessageBuilderTest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.

- **Bổ sung vòng G2 (sau F):** Attachment tổng/hiển thị dùng size snapshot, tính lại sau modal; nút remove theo file/stamp thay index. Vẫn metadata-only, không cryptographic lock/TOCTOU-proof; native chooser/DnD thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailMessageBuilderTest: 5/5 PASS; AcceptanceRegressionTest: 12/12 PASS; MailApiClientNewEndpointsTest: 6/6 PASS; UiLayoutRegressionTest: 6/6 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAX_ATTACHMENT_SIZE_MB. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: Giữ: chọn/xóa chip local không tạo audit; §4 copy gửi thực tế cần SEND_MAIL ở 4369.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4368 (M19, DEFECT/PARTIAL): Open chooser thật nhưng bản trước sửa mở outside fixture; console mới che danh sách shell và chỉ nhập path tuyệt đối fixture. Chọn fixture.txt 20B → chip/tổng 20B, × xóa → hướng dẫn trống; re-add rồi chọn empty-fixture.txt 0B → 2 file/tổng 20B. Ảnh M19-attachments (không chụp chooser). Phần còn lại: Chưa dùng multi-select trong cùng chooser/DnD, vượt quota/thay file sau chọn. Đã sửa chooser bắt đầu ở configured download dir và scroll chips, chưa kiểm lại bản mới. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — Compose bảo vệ attachment edits trong lúc send, hoàn tất DnD accept/reject, filename literal; source và suite tự động, chưa reverify native chooser/DnD. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [Swing](../swing-supplemental-observations.md): Compose khóa attachment edit trong lúc SMTP gửi; giữ DnD và chooser từ mốc trước. C-UI standalone chưa thao tác chooser/DnD do focus không xác nhận. Vòng D: D2: Compose standalone không account/session, 760×620, scale 1.25; hit-test/pointer đúng nhưng không nhận AWT MOUSE_PRESSED, focus vẫn JComboBox. Dừng trước typing/chooser/DnD/send, exit 2 và root đã dọn; chưa chứng minh native input hoặc authenticated UI. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): E regression: recipient chips scroll riêng, giữ vùng input; commit recipient hợp lệ nguyên nhóm, tối đa 50. Compose giữ generation phiên, kiểm lại sau chooser/confirm; attachment regular/readable NOFOLLOW_LINKS, dedup/size; thiếu status SENT hoặc messageId không rỗng không được báo thành công, giữ draft/unknown và không auto resend. Không coi metadata check là TOCTOU-proof. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **Vòng F:** [Swing vòng F (automated)](../swing-supplemental-observations.md): Compose chọn file regular/readable NOFOLLOW_LINKS, normalize/dedupe, metadata stamp size/mtime/fileKey, kiểm trước submit và sau refresh config; DnD chỉ nhận file hợp lệ khi dialog hiện hành và không gửi. AcceptanceRegressionTest 10/10. Không hash/lock toàn nội dung hoặc loại trừ mọi TOCTOU; chưa native chooser/DnD. Không thay trạng thái checkbox. **Vòng G2:** Attachment tổng/hiển thị dùng size snapshot, tính lại sau modal; nút remove theo file/stamp thay index. Vẫn metadata-only, không cryptographic lock/TOCTOU-proof; native chooser/DnD thiếu. **Còn thiếu:** Attachment snapshot/remove theo file đã sửa; còn native chooser cancel/multi-select/DnD/reject, size và sửa file lúc gửi. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4368-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4368-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M11] Màn hình Soạn mail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
