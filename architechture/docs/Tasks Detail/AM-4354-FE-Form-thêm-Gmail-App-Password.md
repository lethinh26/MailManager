# [FE] Form thêm Gmail App Password

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
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Form thêm Gmail App Password` thuộc **[M05] Màn hình Quản lý Gmail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `GmailAccountDialog extends JDialog` (modal, owner=MainFrame)
- `JTable` columns: Email | Display Name | Auth Type | Status | Created At — row từ EP-G1
- Form nhỏ bên dưới: input Email + App Password (JPasswordField, hint "Chỉ dùng Gmail đã bật 2FA + App Password từ myaccount.google.com")
- Nút "Liên kết App Password" → EP-G2 (SwingWorker); "Liên kết Google OAuth2" → EP-O1 rồi `Desktop.browse(authorizeUrl)`; "Xoá" → confirm dialog → EP-G3
- Theo `publicConfig`: `appPasswordLinkEnabled=false` → disable form App Password + show note; `oauth2LinkEnabled=false` → disable nút OAuth2
- Status RED "NEEDS_REAUTH" → nút "Liên kết lại" (chỉ OAuth2) gọi EP-O1 với email cũ

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** GmailManagerDialog Wizard JPasswordField, hướng dẫn 2FA/App Password, kiểm tra email, chuẩn hóa email_key, EP-G2 SwingWorker; báo outcome và tải lại danh sách, xóa ô mật khẩu sau khi gửi. Cờ tắt khóa wizard và kiểm tra lại trước khi gọi API.
- **Tương thích contract:** ApiClient.linkGmailWithAppPassword gửi `intent=UPSERT` tường minh, wizard báo CREATED/METHOD_ADDED/METHOD_UPDATED; request không intent mặc định CREATE, trùng mailbox trả 409 GMAIL_DUPLICATE. Quota MAX_GMAIL_PER_USER áp dụng khi tạo account mới, không chặn cập nhật cùng email_key ở quota tối đa. E2E 14 kiểm wizard tạo/cập nhật cùng ID tại quota 1; chưa xác minh kết quả runtime mới, cần kiểm thêm deny mailbox mới và App Password thật.
- **Test liên quan:** GmailMultiAuthClientTest, EmailNormalizerClientTest, GmailAccountControllerTest, GmailMultiAuthIntegrationTest; credential thật cần test riêng.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Bổ sung vòng G2 (sau F):** Wizard kiểm generation/disposal trước request/sau public config, không publish vào dialog cũ. GmailMultiAuthClientTest 5/5; vẫn thiếu native/live.

- **Bổ sung G3:** Wizard kiểm current session và wizard chưa đóng trước submit, không chạy chồng mutation; refresh metadata khi xong. Không thử credential bị gate.

- **Bổ sung G5:** G5: GmailManagerDialog dùng FeatureAdmission ticket, kiểm sau GET và trước link dispatch; off/on không hồi sinh submit cũ, config null fail-closed, lỗi cũ không ghi đè trạng thái mới; re-enable theo config mới. Giữ clear password, session/wizard guards; không có liên kết Gmail live hay native input mới. SwingFeatureAcceptanceTest 16/16 tự động.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** GmailAccountControllerTest: 15/15 PASS; GmailMultiAuthIntegrationTest: 9/9 PASS; ClientLiveServerE2ETest: 16/16 PASS; EmailNormalizerClientTest: 1/1 PASS; GmailMultiAuthClientTest: 5/5 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_APP_PASSWORD_LINK, MAX_GMAIL_PER_USER. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4354 (M09, PARTIAL): App Password 3 bước thật: hướng dẫn 2FA/16 ký tự → đi tiếp (không click mở Google), điền email test, secret fixture, display name, Kiểm tra IMAP & Liên kết. Wizard bước 3 hiện "LIÊN KẾT THÀNH CÔNG", ACTIVE; manager một mailbox, INBOX 60 thư. Ảnh M09-link-success. Phần còn lại: Không thực hiện password sai/UPSERT/quota deny/new method; Google App Password thật không chạy. E14 riêng không thay observation. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. **Vòng G2:** Wizard kiểm generation/disposal trước request/sau public config, không publish vào dialog cũ. GmailMultiAuthClientTest 5/5; vẫn thiếu native/live. Không đổi checkbox. **Vòng G3:** Wizard kiểm current session và wizard chưa đóng trước submit, không chạy chồng mutation; refresh metadata khi xong. Không thử credential bị gate. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4354-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4354-m). **G5:** G5: GmailManagerDialog dùng FeatureAdmission ticket, kiểm sau GET và trước link dispatch; off/on không hồi sinh submit cũ, config null fail-closed, lỗi cũ không ghi đè trạng thái mới; re-enable theo config mới. Giữ clear password, session/wizard guards; không có liên kết Gmail live hay native input mới. SwingFeatureAcceptanceTest 16/16 tự động.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M05] Màn hình Quản lý Gmail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
