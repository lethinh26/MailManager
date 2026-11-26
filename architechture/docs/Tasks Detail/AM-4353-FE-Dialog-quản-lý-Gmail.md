# [FE] Dialog quản lý Gmail

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M05] Màn hình Quản lý Gmail |
| Owner | TV2 |
| Platform | UserWeb |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 04/11/2026 → 05/11/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Dialog quản lý Gmail` thuộc **[M05] Màn hình Quản lý Gmail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `GmailAccountDialog extends JDialog` (modal, owner=MainFrame)
- `JTable` columns: Email | Display Name | Auth Type | Status | Created At — row từ EP-G1
- Form nhỏ bên dưới: input Email + App Password (JPasswordField, hint "Chỉ dùng Gmail đã bật 2FA + App Password từ myaccount.google.com")
- Nút "Liên kết App Password" → EP-G2 (SwingWorker); "Liên kết Google OAuth2" → EP-O1 rồi `Desktop.browse(authorizeUrl)`; "Xoá" → confirm dialog → EP-G3
- Theo `publicConfig`: `appPasswordLinkEnabled=false` → disable form App Password + show note; `oauth2LinkEnabled=false` → disable nút OAuth2
- Status RED "NEEDS_REAUTH" → nút "Liên kết lại" (chỉ OAuth2) gọi EP-O1 với email cũ

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** GmailManagerDialog modal hiển thị card Email/Display Name/Google + App Password/preferred/active method/Status/Created At; EP-G1 tải nền, đổi tên/gỡ/xóa, cảnh báo đỏ NEEDS_REAUTH và fallback. Giữ card multi-auth thay JTable.
- **Tương thích liên kết:** Client chủ động gửi intent=UPSERT cho cả App Password và OAuth2 (reauth gắn accountId); request legacy không intent giữ CREATE. Chỉ tạo mới mới tiêu hao quota; thêm/cập nhật phương thức cùng mailbox giữ ID. Audit link/unlink/method do BE, không tự ghi trùng ở UI. E2E 14 kiểm App Password ở quota 1; chưa xác minh kết quả runtime mới hoặc Google consent.
- **Test liên quan:** GmailMultiAuthClientTest, MailApiClientNewEndpointsTest, SwingFeatureAcceptanceTest; GmailAccountControllerTest, GmailMultiAuthIntegrationTest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.

- **Bổ sung vòng G2 (sau F):** Client Gmail manager chỉ báo kết nối đủ khi IMAP và SMTP đều true, kiểm session/disposal trước network và callback; conflict409 tải lại metadata không retry mutation. GmailMultiAuthClientTest 5/5. Chưa native thao tác.

- **Bổ sung G3:** Single-flight action; chỉ cho thao tác từ DTO hiện hành, kiểm sau confirm, refresh metadata sau kết quả; 404 không còn bị diễn giải thành dev-only; 401 chuyển login. Automated không thay native.

- **Bổ sung G6:** G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** GmailAccountControllerTest: 15/15 PASS; GmailMultiAuthIntegrationTest: 9/9 PASS; ClientLiveServerE2ETest: 16/16 PASS; GmailMultiAuthClientTest: 5/5 PASS; MailApiClientNewEndpointsTest: 6/6 PASS; SwingFeatureAcceptanceTest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_APP_PASSWORD_LINK, ALLOW_OAUTH2_LINK. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Manager link/UPSERT/unlink trong E14, audit LINK_GMAIL/UPDATE_GMAIL_METHOD/UNLINK_GMAIL; callback và revoke audit qua Newman fixture. Không còn N/A vì có mutation.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4353 (M08, PARTIAL): Manager modal 820×580 hiển thị 0 hộp thư, hai link button; sau wizard thấy card Manual Swing Mailbox, ui_manual@test.local, Google chưa có, ★ App Password, HOẠT ĐỘNG, ngày link/check, 1 hộp thư, nút Đổi tên/Kiểm tra kết nối/Gỡ/Xóa. Ảnh M08-manager. Phần còn lại: Không thực hiện đổi tên, verify riêng, preferred/fallback, gỡ method. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — GmailManagerDialog chỉ nhận kết quả refresh mới nhất và bỏ callback đã dispose; regression refresh response ngược thứ tự. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. **Vòng G2:** Client Gmail manager chỉ báo kết nối đủ khi IMAP và SMTP đều true, kiểm session/disposal trước network và callback; conflict409 tải lại metadata không retry mutation. GmailMultiAuthClientTest 5/5. Chưa native thao tác. **Còn thiếu:** Manager 409 refresh, generation/disposal và partial diagnostic đã có regression; còn native rename/test/preferred/fallback/gỡ method/stale refresh. Không đổi checkbox. **Vòng G3:** Single-flight action; chỉ cho thao tác từ DTO hiện hành, kiểm sau confirm, refresh metadata sau kết quả; 404 không còn bị diễn giải thành dev-only; 401 chuyển login. Automated không thay native. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4353-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4353-m). **G6:** G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M05] Màn hình Quản lý Gmail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
