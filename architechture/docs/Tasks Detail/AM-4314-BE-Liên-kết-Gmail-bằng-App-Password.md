# [BE] Liên kết Gmail bằng App Password

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M04] Liên kết Gmail bằng App Password |
| Owner | TV2 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 02/11/2026 → 04/11/2026 |
| Ước lượng | 4h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Liên kết Gmail bằng App Password` thuộc **[M04] Liên kết Gmail bằng App Password** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-G2 POST /api/gmail-accounts/app-password`

- **Path:** `/api/gmail-accounts/app-password`
- **Method:** POST
- **Role:** AUTHENTICATED | **Category:** Gmail Account

**Mô tả:** Liên kết Gmail bằng App Password {email,appPassword}; test IMAP thật trước khi lưu; AES-256-GCM; log LINK_GMAIL.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

POST /api/gmail-accounts/app-password
- Body `{email, appPassword}` — validate format email
- Kiểm tra cờ `ALLOW_APP_PASSWORD_LINK` → false: 403 FEATURE_DISABLED
- Kiểm tra trùng email (theo user hiện tại) → 409 GMAIL_DUPLICATE
- Đếm `countByUserId` ≥ `MAX_GMAIL_PER_USER` → 409 GMAIL_LIMIT_REACHED
- **Test kết nối thật**: `MailConnectionFactory.createStore(EMAIL, APP_PASSWORD)` → `store.connect("imap.gmail.com", 993, email, appPassword)` → timeout 10s
  - Sai password → `AuthenticationFailedException` → 422 GMAIL_AUTH_FAILED
  - Network/DNS lỗi → `MessagingException` → 502 GMAIL_CONNECTION_ERROR
- Đóng store trong finally
- Đúng → `AesCipher.encrypt(appPassword)` → save `GmailAccount{userId, email, displayName=email, authType=APP_PASSWORD, appPasswordEncrypted, status=ACTIVE}`
- Log `LINK_GMAIL` detail `{email}`
- Response 201 `{id, email, authType, status}`

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** POST /api/gmail-accounts/app-password body email/appPassword, displayName và intent tùy chọn (email hợp lệ tối đa 255, appPassword 8–64, displayName tối đa 255); loại khoảng trắng password, kiểm tra ALLOW_APP_PASSWORD_LINK và quota; thử IMAPS qua MailConnectionFactory.testImap trước AES-GCM/save. Owner được khóa/recheck enabled, bảo trì, feature flag trong transaction để quota đồng thời an toàn. Audit LINK_GMAIL/ADD_GMAIL_METHOD/UPDATE_GMAIL_METHOD, không chứa secret. CREATE mặc định trả 409 GMAIL_DUPLICATE trước probe và recheck trong transaction dưới khóa owner.
- **Khác biệt:** Chỉ intent=UPSERT tường minh mới cho phép thêm/cập nhật phương thức; nhận diện trùng theo (user_id,email_key), bỏ dấu chấm/+tag cho gmail.com/googlemail.com. Tạo mới 201 CREATED; thêm/cập nhật 200 METHOD_ADDED/METHOD_UPDATED, giữ phương thức còn lại. Quota chỉ hộp thư mới (0 không giới hạn). 422 GMAIL_AUTH_FAILED/IMAP_DISABLED/TWO_STEP_VERIFICATION_REQUIRED; 502 GMAIL_CONNECTION_ERROR. Probe IMAPS mở kết nối mới không dùng pool/retry, giới hạn connect/read/write tối đa 10s (hoặc cấu hình ngắn hơn), store được đóng. Kết nối mail thông thường giữ timeout/retry cấu hình riêng.
- **Mã nguồn:** `controller/GmailAccountController.java#linkGmailWithAppPassword`, `dto/LinkAppPasswordRequest.java`, `service/GmailAccountLinkService.java`, `mail/DefaultMailConnectionFactory.java#testImap`, `security/AesCipher.java`, `src/main/resources/application.properties`.
- **Test source:** `controller/GmailAccountControllerTest.java` (success/upsert/disabled/limit/auth/IMAP/2FA); `service/GmailMultiAuthIntegrationTest.java` (CREATE trùng trả 409 không probe, UPSERT tường minh và concurrentDifferentMailboxesCannotExceedUserLimit). IMAP thật với Google và timeout thực tế vẫn cần xác minh thủ công.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Bổ sung vòng G2 (sau F):** V9 version guard bảo vệ mailbox khi link/update cạnh tranh; stale probe không mở tiếp SMTP; GmailAccountControllerTest 15/15, GmailMultiAuthIntegrationTest 9/9. Chưa có manual link credential/quota/flag mới.

- **Bổ sung G3:** Owner/id/revision preflight tại factory; snapshot lỗi bị từ chối, không đọc lại grant khác. Manual link credential/flag/quota còn thiếu.

- **Bổ sung G4:** Raw caller scope close quan sát G4 R02–R05/R09; không phải link sai App Password hoặc Google thật.

- **Bổ sung G6:** Giữ shared 4359-M.03: actual POST link wrong App Password, network/DNS error mapping, close và dedicated 2FA/probe/AES persistence/audit. F204/F205 không phải POST link sai; giữ H/L.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** GmailAccountControllerTest: 15/15 PASS; GmailMultiAuthIntegrationTest: 9/9 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). CREATE mặc định 409 GMAIL_DUPLICATE; UPSERT chỉ khi intent tường minh. E12/E14 chứng minh probe IMAPS và giữ ID đúng trên fixture; timeout connect/read/write tối đa 10s mỗi socket, chưa đo deadline DNS/TLS tổng với Gmail thật.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_APP_PASSWORD_LINK, MAX_GMAIL_PER_USER. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4314 (H refs trong hàng AM-4314, PARTIAL): H082 duplicate 409 GMAIL_DUPLICATE, H083 explicit update 200 METHOD_UPDATED giữ id 2, H105 tạo lại 201 CREATED id 6 ACTIVE; H106 mở IMAP fixture. H116 flag App Password=false; H121 GET list 200 không test link. Phần còn lại: POST link hợp lệ khi disabled, credential sai, validation, quota và audit link; Gmail thật 2FA/App Password, timeout/DNS. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): F204/F205, F211: provider deny trên mailbox đã link trả 422 và không delivery; F209 restore folders 200. Đây không phải POST link với App Password sai/flag disabled/quota. Không thay trạng thái checkbox. **Vòng G2:** V9 version guard bảo vệ mailbox khi link/update cạnh tranh; stale probe không mở tiếp SMTP; GmailAccountControllerTest 15/15, GmailMultiAuthIntegrationTest 9/9. Chưa có manual link credential/quota/flag mới. Không đổi checkbox. **Vòng G3:** Owner/id/revision preflight tại factory; snapshot lỗi bị từ chối, không đọc lại grant khác. Manual link credential/flag/quota còn thiếu. Không đổi checkbox. **G4:** Raw caller scope close quan sát G4 R02–R05/R09; không phải link sai App Password hoặc Google thật. [AM-4314-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4314-m). **G6:** Giữ shared 4359-M.03: actual POST link wrong App Password, network/DNS error mapping, close và dedicated 2FA/probe/AES persistence/audit. F204/F205 không phải POST link sai; giữ H/L.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M04] Liên kết Gmail bằng App Password**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
