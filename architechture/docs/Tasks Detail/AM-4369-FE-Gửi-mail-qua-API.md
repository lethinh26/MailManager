# [FE] Gửi mail qua API

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
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Gửi mail qua API` thuộc **[M11] Màn hình Soạn mail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

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

- **Code và hành vi:** ComposeDialog.performSend: snapshot dữ liệu hợp lệ, refresh public config và giới hạn, ApiClient.sendMailMultipart trong SwingWorker (BodyPublishers.ofFile, không nạp toàn bộ attachment vào heap); progress/disable, success dialog và refresh, không đóng trong lúc đang gửi.
- **Test liên quan:** MailApiClientNewEndpointsTest, AcceptanceRegressionTest, MailMessageBuilderTest, MailServiceGreenMailIntegrationTest; SMTP Gmail thật chưa xác minh.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Bổ sung vòng G2 (sau F):** Compose chặn mailbox cần reauth nếu không có method thay thế; reply/thread và attachment snapshot an toàn hơn; giữ F unknown delivery/draft/no-auto-resend. Automated không thay native.

- **Bổ sung G3:** Compose 401 chuyển login; giữ unknown delivery/no-auto-resend. Không có manual SMTP mới.

- **Bổ sung G5:** G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailMessageBuilderTest: 5/5 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; AcceptanceRegressionTest: 12/12 PASS; ClientLiveServerE2ETest: 16/16 PASS; MailApiClientNewEndpointsTest: 6/6 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_SEND_MAIL, MAX_ATTACHMENT_SIZE_MB. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4369 (M20, PARTIAL): SMTP thật: HTML gửi self + Cc ui_other → nút gửi disabled/status "Đang gửi thư qua SMTP bảo mật...", modal "Đã gửi thư thành công!" (M20-send-success), search self thấy đúng marker, ui_other thấy thư CC. Lần hai gửi text To=2 fixture recipients + Bcc fixture, 20B/0B, success, search self → body "Manual attachment body fixture", hai chip filename/0B, đúng 2 To. Phần còn lại: Không kiểm received Bcc mailbox/audit SEND_MAIL từ Admin UI, attachment bytes của thư **gửi lại** độc lập (đã kiểm hash download ở M30), fault/retry, real Gmail SMTP. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — Compose bỏ kết quả send thuộc dialog dispose/session cũ, không ghi public config cũ và không bắt đầu send khi session đổi; regression tự động. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [Swing](../swing-supplemental-observations.md): Không tự resend khi SMTP outcome chưa rõ; bảo toàn draft, xác nhận riêng trước retry; 503 AUDIT_UNAVAILABLE chắc chắn chưa gửi, không đánh đồng với uncertain. C gửi thật/read-back + regression Swing, chưa gửi trực tiếp từ phiên Swing. Vòng D: D011/D012 capacity rejection trước SMTP mutation; Client E2E và regression chạy mới. D2: Compose standalone không account/session, 760×620, scale 1.25; hit-test/pointer đúng nhưng không nhận AWT MOUSE_PRESSED, focus vẫn JComboBox. Dừng trước typing/chooser/DnD/send, exit 2 và root đã dọn; chưa chứng minh native input hoặc authenticated UI. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): E regression: recipient chips scroll riêng, giữ vùng input; commit recipient hợp lệ nguyên nhóm, tối đa 50. Compose giữ generation phiên, kiểm lại sau chooser/confirm; attachment regular/readable NOFOLLOW_LINKS, dedup/size; thiếu status SENT hoặc messageId không rỗng không được báo thành công, giữ draft/unknown và không auto resend. Không coi metadata check là TOCTOU-proof. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. E023–041: reject INSERT audit, gửi 200; CONFIRMED giữ attempts/nextAttemptAt qua restart. Reject UPDATE outcome, gửi 200; PREPARED và receipt bền vững, localDeferred1 vẫn khôi phục sau restart thật. Restore outcome: CONFIRMED2/localDeferred0; restore audit: queue0, đúng 2 SEND_MAIL cùng hai eventId và actorId, mỗi thư read-back đúng 1, không gửi lại. V8 không suy PREPARED thành success, không atomic MySQL–Gmail; nếu crash trước receipt durable hoặc cả hai kho mất dữ liệu thì vẫn phải đối chiếu. **Vòng F:** [Swing vòng F (automated)](../swing-supplemental-observations.md) và [F201–F226](../backend-supplemental-observations.md): Attachment stamp đổi thì dừng trước submission; giữ draft/uncertain/no automatic resend. F213–217 Backend lỗi sau delivery, đúng một marker và audit uncertain, không retry; Client E2E16/16 và AcceptanceRegressionTest10/10, không phải GUI manual. Không thay trạng thái checkbox. **Vòng G2:** Compose chặn mailbox cần reauth nếu không có method thay thế; reply/thread và attachment snapshot an toàn hơn; giữ F unknown delivery/draft/no-auto-resend. Automated không thay native. Không đổi checkbox. **Vòng G3:** Compose 401 chuyển login; giữ unknown delivery/no-auto-resend. Không có manual SMTP mới. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4369-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4369-m). **G5:** G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M11] Màn hình Soạn mail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
