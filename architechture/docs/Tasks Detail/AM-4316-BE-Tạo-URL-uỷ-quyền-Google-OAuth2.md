# [BE] Tạo URL uỷ quyền Google OAuth2

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M06] Liên kết Gmail bằng OAuth2 |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 09/11/2026 → 10/11/2026 |
| Ước lượng | 4h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Tạo URL uỷ quyền Google OAuth2` thuộc **[M06] Liên kết Gmail bằng OAuth2** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-O1 GET /api/oauth2/authorize-url`

- **Path:** `/api/oauth2/authorize-url`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** OAuth2

**Mô tả:** Tạo URL uỷ quyền Google + state gắn user; kiểm tra ALLOW_OAUTH2_LINK + MAX_GMAIL_PER_USER.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/oauth2/authorize-url
- JWT required; check `ALLOW_OAUTH2_LINK` → 403 FEATURE_DISABLED
- Đếm gmail của user → ≥ MAX_GMAIL_PER_USER: 409 GMAIL_LIMIT_REACHED
- Sinh `state` = UUID.randomUUID() gắn `userId` + expiry 5 phút → lưu `OauthStateStore` (in-memory hoặc DB ttl)
- Build URL: `https://accounts.google.com/o/oauth2/v2/auth?client_id=${GOOGLE_CLIENT_ID}&redirect_uri=${REDIRECT_URI}&response_type=code&scope=https://mail.google.com/&access_type=offline&prompt=consent&state=${state}`
- Response 200 `{authorizeUrl}`
- Client: `Desktop.getDesktop().browse(URI.create(authorizeUrl))` → mở browser mặc định

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** GET /api/oauth2/authorize-url, JWT; ALLOW_OAUTH2_LINK false→403 FEATURE_DISABLED, giới hạn mới→409 GMAIL_LIMIT_REACHED. Thêm ?accountId=... (chỉ hộp thư sở hữu; 404 ACCOUNT_NOT_FOUND nếu sai; không tính quota khi cấp lại). State ngẫu nhiên 24 byte, PKCE S256, TTL state và poll đang chờ 5 phút; scope Gmail + openid/email/profile, offline/consent. CREATE mặc định kiểm tra quota khi khởi tạo. intent=UPSERT tường minh cho phép bổ sung phương thức cả khi quota đầy; quota cho hộp thư mới được recheck ở callback. accountId mặc định UPSERT; CREATE kết hợp accountId trả 400.
- **Khác biệt:** 200 {authorizeUrl,pollToken}, no-store, pollToken 256 bit theo owner; GET /api/oauth2/link/poll trả 202 PENDING/200 SUCCESS|FAILED, body {status,accountId,outcome,code,message}; 404 LINK_SESSION_NOT_FOUND/410 LINK_SESSION_EXPIRED. Mở rộng này và PKCE với state ngẫu nhiên 24 byte (thay UUID; giữ TTL 5 phút) để theo dõi đúng giao dịch và chống replay/chọn sai hộp thư; không lộ client secret.
- **Mã nguồn:** `controller/OAuth2Controller.java`, `dto/AuthorizeUrlResponse.java`, `oauth2/OAuth2ServiceImpl.java#getAuthorizeUrl` và pollLink, `oauth2/OAuth2StateStore.java`, `oauth2/PkceUtil.java`.
- **Test source:** `oauth2/OAuth2ServiceTest.java` (authorize URL, flag, limit), `oauth2/OAuth2StateStoreTest.java#stateAndPendingPollExpireAfterFiveMinutesWithoutSleeping`, `oauth2/OAuth2MockWebServerTest.java` (poll owner/single-use, targeted callback). Mở trình duyệt/Google consent thật còn cần thử.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** OAuth2MockWebServerTest: 21/21 PASS; OAuth2ServiceTest: 6/6 PASS; OAuth2StateStoreTest: 3/3 PASS; OAuth2ControllerTest: 4/4 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). URL authorization và state/poll pending TTL 5 phút; PKCE và pollToken bổ sung giữ chống replay/owner binding.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_OAUTH2_LINK, MAX_GMAIL_PER_USER. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Tạo URL/poll là chuẩn bị OAuth; callback/link thực mới ghi audit.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4316 (H refs trong hàng AM-4316, PARTIAL): H041 flag disabled 403; H045 authorize URL/poll token có mặt 200, H046 own poll 202 PENDING, H047 other owner 404 LINK_SESSION_NOT_FOUND; H107 sai route 404, H108 đúng route 200. Retest bản cuối: [F003](../backend-final-observations.md) (PARTIAL): GET authorize-url actor owner 200, Cache-Control no-store, authorizeUrl và pollToken chỉ ghi [present]; H041 disabled 403, H046 pending, H047 foreign poll 404, H108 đúng route 200.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G017–022, G064–065: CREATE quota 409, UPSERT method hiện hữu 200, HTTPS/Gmail scope/offline/PKCE S256/state/poll/redirect shape đúng; state thực quá 5 phút, callback HTML lỗi và poll 410. Phần còn lại hiện tại: Chưa mở browser/Google consent thật từ Client; không lặp lại các nhánh local quota/TTL/shape/owner đã có evidence. Tách riêng human sign-off, live, contract và kết quả tự động. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4316-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4316-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M06] Liên kết Gmail bằng OAuth2**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
