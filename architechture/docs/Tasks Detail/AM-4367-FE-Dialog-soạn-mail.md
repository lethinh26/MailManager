# [FE] Dialog soạn mail

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
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Dialog soạn mail` thuộc **[M11] Màn hình Soạn mail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

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

- **Code và hành vi:** ComposeDialog modal, From combo, To/Cc/Bcc EmailChipInput hỗ trợ dấu phẩy/chấm phẩy, subject/body HTML toggle, validation trước khi gửi; API payload multipart hiện hành với UTF-8. Giữ chips thay textfield đơn giản.
- **Test liên quan:** AcceptanceRegressionTest#composeRejectsInvalidRecipientsOversizedBodyAndChangedFiles, MailApiClientNewEndpointsTest, MailMessageBuilderTest, SwingFeatureAcceptanceTest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.

- **Bổ sung vòng G2 (sau F):** Reply/ReplyAll tôn trọng Reply-To, bỏ self alias/Bcc, dedup To/Cc, Message-ID/References sạch, không append null; quote HTML-only thành text không fetch tài nguyên. AcceptanceRegressionTest 12/12; chưa native.

- **Bổ sung G5:** G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailMessageBuilderTest: 5/5 PASS; AcceptanceRegressionTest: 12/12 PASS; ClientLiveServerE2ETest: 16/16 PASS; MailApiClientNewEndpointsTest: 6/6 PASS; SwingFeatureAcceptanceTest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_SEND_MAIL, MAX_ATTACHMENT_SIZE_MB. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4367 (M18, PARTIAL): ComposeDialog 760×620 từ toolbar, From combo chỉ linked fixture account, To/Cc/Bcc, HTML toggle; bấm Send trống → "Vui lòng nhập ít nhất một địa chỉ người nhận (Đến)." Điền To/CC, subject/body, HTML toggle, send success. Lần sau To nhiều email ; và Bcc, attach 20B/0B. Ảnh M18-compose, M19-attachments. Phần còn lại: Không test invalid email, >50 recipients/body 1MB, draft cancel No/Yes, keyboard chip entry/HTML formatting đủ. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — Compose giữ cả recipient chip đã commit và input chưa commit khi giữ draft; phím comma/semicolon/Enter được xử lý, regression giữ draft. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [Swing](../swing-supplemental-observations.md): Compose khóa toàn bộ field/chip/format/file edit lúc gửi, giữ draft khi fail, unknown outcome cảnh báo và resend cần xác nhận riêng; SwingFeatureAcceptanceTest 9/9. C-UI standalone mở cửa sổ thật không account/session; dừng native input do focus không xác nhận, không chứng minh thao tác soạn thủ công. Vòng D: D2: Compose standalone không account/session, 760×620, scale 1.25; hit-test/pointer đúng nhưng không nhận AWT MOUSE_PRESSED, focus vẫn JComboBox. Dừng trước typing/chooser/DnD/send, exit 2 và root đã dọn; chưa chứng minh native input hoặc authenticated UI. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): E regression: recipient chips scroll riêng, giữ vùng input; commit recipient hợp lệ nguyên nhóm, tối đa 50. Compose giữ generation phiên, kiểm lại sau chooser/confirm; attachment regular/readable NOFOLLOW_LINKS, dedup/size; thiếu status SENT hoặc messageId không rỗng không được báo thành công, giữ draft/unknown và không auto resend. Không coi metadata check là TOCTOU-proof. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **Vòng F:** [Swing vòng F (automated)](../swing-supplemental-observations.md): Compose safeLinkHref chỉ http/https, host hợp lệ, không userInfo, escape href; kiểm dialog còn hiện hành sau modal và không sending. AcceptanceRegressionTest 10/10. Chưa native recipient/draft/reply/forward. Không thay trạng thái checkbox. **Vòng G2:** Reply/ReplyAll tôn trọng Reply-To, bỏ self alias/Bcc, dedup To/Cc, Message-ID/References sạch, không append null; quote HTML-only thành text không fetch tài nguyên. AcceptanceRegressionTest 12/12; chưa native. **Còn thiếu:** Reply-To/self/Bcc/dedup/thread/HTML-only đã có automated. Còn native input comma/semicolon/Enter, invalid/limit recipient/body, draft No/Yes, reply/forward thật. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4367-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4367-m). **G5:** G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M11] Màn hình Soạn mail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
