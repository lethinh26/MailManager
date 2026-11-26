# [FE] SwingWorker + trạng thái tải

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M09] Màn hình Hộp thư & Chi tiết mail |
| Owner | TV4 |
| Platform | UserWeb |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 07/11/2026 → 08/11/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] SwingWorker + trạng thái tải` thuộc **[M09] Màn hình Hộp thư & Chi tiết mail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `MailPanel extends JPanel` trong MainFrame
- `JTree` bên trái: root "Tài khoản" → node mỗi Gmail (icon) → folder children
- `JTable` mail ở giữa: columns From | Subject | Date | (icon 📎) — unread in đậm (`Font.BOLD` renderer)
- `DefaultTableModel` không editable; double-click row → mở `MailDetailPanel` (hoặc dialog)
- Phân trang: "Trang X/Y", nút Prev/Next, `JComboBox` page size (10/20/50)
- Nút "Làm mới" → reload EP-M2; `JTextField` search + nút "Tìm" → EP-M2 với `q=`
- Mọi API call trong `SwingWorker` + status bar "Đang tải..."
- Sau khi mở detail (EP-M3) → quay lại refresh table (mail chuyển read)

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** MainFrame/ComposeDialog/GmailManagerDialog/AdminFrame thực hiện I/O nền SwingWorker, trạng thái tải/error; MailboxViewModel workerCoordinator hủy/khoanh vùng generation khi đổi account/folder/search; không ghi đè UI bằng response cũ.
- **Test liên quan:** MailboxRegressionTest, OAuthFlowAndSessionTest, AcceptanceRegressionTest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** MainFrame và AdminFrame đều bảo vệ callback theo generation; SwingFeatureAcceptanceTest#adminIgnoresOlderSearchAndDisablesSettingsUntilLoaded.

- **Bổ sung vòng G2 (sau F):** Manager bổ sung generation/disposal trước request và done, stale conflict không retry mutation; Composer callback giữ guard F. Automated, chưa native.

- **Bổ sung G3:** Generation guard cho batch/delete/flag/cache và cleanup dialog, 401 Manager/Compose; không thay native stale/expiry.

- **Bổ sung G4:** G4 Admin workers guard window/session/role, mutation single-flight/recheck target, late user rows sau dispose không publish; SwingFeatureAcceptanceTest 14/14. Native transition vẫn OPEN.

- **Bổ sung G6:** G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AcceptanceRegressionTest: 12/12 PASS; MailboxRegressionTest: 13/13 PASS; OAuthFlowAndSessionTest: 5/5 PASS; SwingFeatureAcceptanceTest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE; worker phản ánh lỗi/đóng session. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: Giữ: worker/loading không tự audit; mutation chạy qua worker vẫn audit đúng action.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4366 (M17, PARTIAL): Quan sát "Đang tạo tài khoản..." form disable, "Đang đăng nhập..." disable, MainFrame "Đang tải danh sách thư mục..." list disabled, SMTP progress status + Send disabled; sau đó dữ liệu/response thực tế. Phần còn lại: Không cố tình delay/đứt mạng shared fixture, kéo window khi worker, race response/close while loading. Console IPC error không phải network error Client. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — Callback stale theo mailbox/session/detail và dispose được bỏ qua; regression pagination/manager/compose, không phải thao tác delay thủ công. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [Swing](../swing-supplemental-observations.md): MainFrame config refresh bỏ callback session generation cũ; config unavailable disable send fail-closed. BE partial delivery/outcome mapping và FE 31/31 suite trọng điểm, không thay manual race. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): E regression: recipient chips scroll riêng, giữ vùng input; commit recipient hợp lệ nguyên nhóm, tối đa 50. Compose giữ generation phiên, kiểm lại sau chooser/confirm; attachment regular/readable NOFOLLOW_LINKS, dedup/size; thiếu status SENT hoặc messageId không rỗng không được báo thành công, giữ draft/unknown và không auto resend. Không coi metadata check là TOCTOU-proof. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **Vòng F:** [Swing vòng F (automated)](../swing-supplemental-observations.md): Vòng F thêm current-session/dialog guards cho Compose và Admin CSV, kiểm lại trước publish; AdminModuleClientTest 5/5, SwingFeatureAcceptanceTest 13/13. Automated, chưa native input. Không thay trạng thái checkbox. **Vòng G2:** Manager bổ sung generation/disposal trước request và done, stale conflict không retry mutation; Composer callback giữ guard F. Automated, chưa native. Không đổi checkbox. **Vòng G3:** Generation guard cho batch/delete/flag/cache và cleanup dialog, 401 Manager/Compose; không thay native stale/expiry. Không đổi checkbox. **G4:** G4 Admin workers guard window/session/role, mutation single-flight/recheck target, late user rows sau dispose không publish; SwingFeatureAcceptanceTest 14/14. Native transition vẫn OPEN. [AM-4366-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4366-m). **G6:** G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M09] Màn hình Hộp thư & Chi tiết mail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
