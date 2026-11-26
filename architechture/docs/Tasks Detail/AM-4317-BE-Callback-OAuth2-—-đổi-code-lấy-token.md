# [BE] Callback OAuth2 — đổi code lấy token

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M06] Liên kết Gmail bằng OAuth2 |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 10/11/2026 → 13/11/2026 |
| Ước lượng | 6h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Callback OAuth2 — đổi code lấy token` thuộc **[M06] Liên kết Gmail bằng OAuth2** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-O2 GET /api/oauth2/callback`

- **Path:** `/api/oauth2/callback`
- **Method:** GET
- **Role:** PUBLIC | **Category:** OAuth2

**Mô tả:** Google callback: đổi code -> token, lấy email, lưu refresh_token mã hoá; trả HTML kết quả.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/oauth2/callback?code=&state=
- KHÔNG JWT — xác thực bằng `state` lookup trong store (expired/invalid → HTML lỗi)
- POST `https://oauth2.googleapis.com/token` body `code, client_id, client_secret, redirect_uri, grant_type=authorization_code` → parse `access_token + refresh_token + expires_in`
- GET `https://gmail.googleapis.com/gmail/v1/users/me/profile` Bearer access_token → lấy `emailAddress`
- Kiểm tra trùng `existsByUserIdAndEmailIgnoreCase` → HTML "Gmail này đã liên kết"
- `AesCipher.encrypt(refreshToken)` → save `GmailAccount{authType=OAUTH2, refreshTokenEncrypted, status=ACTIVE}`
- Xoá state khỏi store (one-time use)
- Log `OAUTH2_CALLBACK` `{email}`
- Response `Content-Type: text/html` đơn giản "Liên kết Gmail thành công — quay lại ứng dụng" (không redirect vì không có deep-link)

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** GET callback public trả HTML 200; state tiêu thụ một lần trước token exchange kèm code_verifier. Kiểm scope Gmail và Google UserInfo email_verified=true và Gmail REST profile GET /gmail/v1/users/me/profile với cùng Bearer token; emailAddress phải khớp email đã xác thực (không phân biệt hoa thường); profile lỗi/thiếu email trả GOOGLE_PROFILE_ERROR, lệch email trả ACCOUNT_MISMATCH trước ghi DB, recheck user/enabled/feature flag/bảo trì; AES-GCM refresh token, CREATE mặc định hoặc UPSERT tường minh theo owner + email_key trong transaction. accountId mục tiêu phải khớp đúng hộp thư còn tồn tại, nếu không ACCOUNT_MISMATCH. Hoàn tất poll SUCCESS/FAILED; không gửi bí mật ra HTML.
- **Khác biệt:** CREATE mặc định từ chối email đã liên kết (HTML thất bại, poll FAILED/GMAIL_DUPLICATE); UPSERT tường minh thêm/cập nhật phương thức và giữ App Password; Google profile và UserInfo được đối chiếu để xác minh danh tính; audit OAUTH2_CALLBACK đúng §4 (một dòng gồm outcome=CREATED/METHOD_ADDED/METHOD_UPDATED và email che bớt); truy vấn audit tương thích các action cũ, thiếu scope LINK_GMAIL_DENIED_SCOPE. Luồng Google login riêng cũng dùng upsert/quota và email đã xác thực. Poll giữ kết quả cuối một lần, không redirect.
- **Mã nguồn:** `oauth2/OAuth2ServiceImpl.java#handleCallback`, `oauth2/OAuth2StateStore.java`, `service/GmailAccountLinkService.java#upsertOAuth2`, `controller/OAuth2Controller.java#handleCallback`.
- **Bảo vệ bổ sung:** callback liên kết và transaction upsert kiểm tra mustChangePassword sau khi nhận quyền Google; nếu Admin reset trong lúc chờ thì từ chối PASSWORD_CHANGE_REQUIRED. Google login công khai vẫn có thể cấp JWT để user đổi mật khẩu, nhưng không tự liên kết Gmail trước khi đổi.
- **Test source:** `oauth2/OAuth2MockWebServerTest.java` (testHandleCallbackFullSuccess, targetedCallbackCannotLinkAnotherGoogleAccount, featureDisabledAfterAuthorizationCompletesPollWithoutContactingGoogle, mandatoryPasswordChangeAfterAuthorizationRejectsCallback, googleLoginCanAuthenticateButCannotAutoLinkBeforePasswordChange, duplicate/upsert, missing scope, denied, invalid state), `service/GmailMultiAuthIntegrationTest.java#pendingAuthenticatedLinkCannotBypassPasswordReset`. `controller/AdminIntegrationTest.java#auditAliasesFindOldAndNewRowsWithoutDuplicatingEvents` kiểm tra alias; Mock HTTP không chứng minh consent/Google thật. Bổ sung createCallbackRejectsDuplicateWithoutReplacingCredentials, gmailProfileMustMatchVerifiedIdentity, gmailProfileHttpErrorFailsClosed; full success kiểm tra request profile/Bearer.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

- **Bổ sung vòng G2 (sau F):** Refresh cache ràng buộc grant/owner, không cache trước lưu; kiểm token/type/TTL/scope, parse đúng error field, persist rotated refresh, NEEDS_REAUTH chặn refresh đã biết sai. OAuth2MockWebServerTest 21/21. Không chứng minh consent/revoke Google thật.

- **Bổ sung G3:** MailboxRevisionChainIntegrationTest 2/2: detached refresh rotation commit và cache stale reject trước provider. Local H2/mock không thay consent/token/revoke Google thật.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; OAuth2MockWebServerTest: 21/21 PASS; GmailMultiAuthIntegrationTest: 9/9 PASS; OAuth2ControllerTest: 4/4 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Callback dùng verified UserInfo + Gmail profile emailAddress, PKCE, state một lần, CREATE duplicate và UPSERT tường minh. Newman O02–O31 đã chạy qua JAR, provider loopback xác minh 4 exchange/profile; chưa consent Google thật.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_OAUTH2_LINK, MAX_GMAIL_PER_USER. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). OAUTH2_CALLBACK cho thành công, một dòng có outcome và email che; alias lịch sử, test OAuth mock.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4317 (H refs trong hàng AM-4317, PARTIAL): H048–050 Accept JSON 500, poll vẫn pending, list rỗng; H051–053 HTML 200 và poll SUCCESS CREATED id 4; H054–056 duplicate HTML lỗi, poll FAILED GMAIL_DUPLICATE; H057–059 explicit relink METHOD_UPDATED giữ id 4. H109 deny HTML, H110 FAILED OAUTH2_DENIED, H111 consumed poll 410 LINK_SESSION_EXPIRED, H112 lặp deny HTML. Provider loopback 4 exchange/userinfo/profile (agent chính báo). Retest bản cuối: [F004](../backend-final-observations.md) (OBSERVED): Callback với Accept không tương thích 406 JSON NOT_ACCEPTABLE, code=errorCode, path đúng; thay cho lỗi H048 500.; [F005](../backend-final-observations.md) (OBSERVED): GET link/poll 202 PENDING, accountId/outcome/code null sau 406; không thấy kết quả liên kết giả.; [F006](../backend-final-observations.md) (OBSERVED): Callback Accept text/html 200, 6882 byte, no-store; HTML hiển thị liên kết thành công và email Google đã che.; [F007](../backend-final-observations.md) (PARTIAL): Poll 200 SUCCESS CREATED accountId=3; F008 list account 3 ACTIVE OAuth2. Sửa 406→pending→HTML→success đã reverify. H051–059 có success/duplicate/explicit UPSERT, H109–112 deny/FAILED/consumed poll 410.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G023–040, G064–066: invalid/expired state, replay, missing scope, profile mismatch đều fail; link không đổi; normal provider SUCCESS; audit OAUTH2_CALLBACK thành công và LINK_GMAIL_DENIED_SCOPE, DTO không credential. Phần còn lại tại mốc trước F: Google token/profile/consent/revoke thật vẫn thiếu; provider loopback không chứng minh Google live. Kiểm privacy log runtime đã scrubbed và mã hóa lưu qua live link, không in secret. Tách riêng human sign-off, live, contract và kết quả tự động. Bổ sung A/B và regression cuối: [backend-supplemental-observations.md](../backend-supplemental-observations.md) — Thay đổi retry link chỉ áp dụng va chạm uk_user_gmail_key; lỗi audit/DB khác không được coi là race có thể retry. GmailMultiAuthIntegrationTest đã chạy; Google thật vẫn thiếu. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): F219–224: authorize/callback/poll local bình thường, short-lived grant rồi provider refresh thực trả invalid_grant; 422 GMAIL_NEEDS_REAUTH, account NEEDS_REAUTH, mailbox App Password không đổi. Không phải consent/token/revoke Google thật. Không thay trạng thái checkbox. **Vòng G2:** Refresh cache ràng buộc grant/owner, không cache trước lưu; kiểm token/type/TTL/scope, parse đúng error field, persist rotated refresh, NEEDS_REAUTH chặn refresh đã biết sai. OAuth2MockWebServerTest 21/21. Không chứng minh consent/revoke Google thật. Không đổi checkbox. **Vòng G3:** MailboxRevisionChainIntegrationTest 2/2: detached refresh rotation commit và cache stale reject trước provider. Local H2/mock không thay consent/token/revoke Google thật. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4317-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4317-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M06] Liên kết Gmail bằng OAuth2**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
