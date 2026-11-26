# [FE] Ẩn nút thêm theo cờ

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M05] Màn hình Quản lý Gmail |
| Owner | TV2 |
| Platform | UserWeb |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 06/11/2026 → 08/11/2026 |
| Ước lượng | 0.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Ẩn nút thêm theo cờ` thuộc **[M05] Màn hình Quản lý Gmail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `GmailAccountDialog extends JDialog` (modal, owner=MainFrame)
- `JTable` columns: Email | Display Name | Auth Type | Status | Created At — row từ EP-G1
- Form nhỏ bên dưới: input Email + App Password (JPasswordField, hint "Chỉ dùng Gmail đã bật 2FA + App Password từ myaccount.google.com")
- Nút "Liên kết App Password" → EP-G2 (SwingWorker); "Liên kết Google OAuth2" → EP-O1 rồi `Desktop.browse(authorizeUrl)`; "Xoá" → confirm dialog → EP-G3
- Theo `publicConfig`: `appPasswordLinkEnabled=false` → disable form App Password + show note; `oauth2LinkEnabled=false` → disable nút OAuth2
- Status RED "NEEDS_REAUTH" → nút "Liên kết lại" (chỉ OAuth2) gọi EP-O1 với email cũ

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** GmailManagerDialog applyPublicConfig ẩn nút từng phương thức; khóa wizard đang mở, MainFrame ẩn CTA reauth khi cờ tắt/không có OAuth; refresh 15 giây và backend vẫn chặn request bị tắt.
- **Test liên quan:** SwingFeatureAcceptanceTest, GmailMultiAuthIntegrationTest, OAuth2MockWebServerTest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.

- **Bổ sung vòng G2 (sau F):** Manager applyPublicConfig/start/wizard giữ generation; public flags và stale callback tự động regression, chưa native.

- **Bổ sung G3:** Manager/wizard kết hợp flag hiện hành với trạng thái loading/operationPending; không mở wizard khi list stale. Native flag transition vẫn OPEN.

- **Bổ sung G5:** G5: GmailManagerDialog dùng FeatureAdmission ticket, kiểm sau GET và trước link dispatch; off/on không hồi sinh submit cũ, config null fail-closed, lỗi cũ không ghi đè trạng thái mới; re-enable theo config mới. Giữ clear password, session/wizard guards; không có liên kết Gmail live hay native input mới. SwingFeatureAcceptanceTest 16/16 tự động.

- **Bổ sung G6:** G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** OAuth2MockWebServerTest: 21/21 PASS; GmailMultiAuthIntegrationTest: 9/9 PASS; ClientLiveServerE2ETest: 16/16 PASS; SwingFeatureAcceptanceTest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_APP_PASSWORD_LINK, ALLOW_OAUTH2_LINK. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: Giữ riêng việc ẩn/khóa link UI: không mutation; khi link thành công audit ở 4354/4355.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4357 (M12, BLOCKED): Manager trước đổi flag hiện App Password/Google. Prime xác nhận cả App Password=false/send=false/registration=false và OAuth=true sau đó, nhưng không đăng nhập lại được. Phần còn lại: Không quan sát manager/wizard đang mở khi tắt App Password, không kiểm nút reauth/Google bị ẩn. Không suy ra từ server config. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. **Vòng G2:** Manager applyPublicConfig/start/wizard giữ generation; public flags và stale callback tự động regression, chưa native. Không đổi checkbox. **Vòng G3:** Manager/wizard kết hợp flag hiện hành với trạng thái loading/operationPending; không mở wizard khi list stale. Native flag transition vẫn OPEN. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4357-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4357-m). **G5:** G5: GmailManagerDialog dùng FeatureAdmission ticket, kiểm sau GET và trước link dispatch; off/on không hồi sinh submit cũ, config null fail-closed, lỗi cũ không ghi đè trạng thái mới; re-enable theo config mới. Giữ clear password, session/wizard guards; không có liên kết Gmail live hay native input mới. SwingFeatureAcceptanceTest 16/16 tự động. **G6:** G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M05] Màn hình Quản lý Gmail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
