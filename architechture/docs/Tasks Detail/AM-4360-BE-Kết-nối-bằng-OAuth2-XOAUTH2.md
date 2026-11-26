# [BE] Kết nối bằng OAuth2 XOAUTH2

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M07] MailConnectionFactory — kết nối Gmail dùng chung |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 09/11/2026 → 11/11/2026 |
| Ước lượng | 3h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Kết nối bằng OAuth2 XOAUTH2` thuộc **[M07] MailConnectionFactory — kết nối Gmail dùng chung** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `MailConnectionFactory` interface:
  - `Store createImapStore(GmailAccount acc)` — `imaps://imap.gmail.com:993`, auth theo `authType`
  - `Transport createSmtpTransport(GmailAccount acc)` — `smtp.gmail.com:587` STARTTLS
  - `Session createSession(GmailAccount acc, Protocol proto)`
- App Password impl: `session.setPasswordAuthentication(new PasswordAuthentication(email, decrypt(appPasswordEncrypted)))`
- OAuth2 impl: dùng `MailConnectionFactory.getValidAccessToken(acc)` → set SASL XOAUTH2 (`OAuth2SaslClientFactory`) hoặc `mail.imaps.sasl.mechanisms=XOAUTH2`
- Timeout thống nhất: `mail.imaps.connectiontimeout=10000`, `mail.imaps.timeout=15000`, `mail.smtp.connectiontimeout=10000`
- TLS: `mail.imaps.ssl.enable=true`, `mail.smtp.starttls.enable=true`, `mail.smtp.ssl.trust=smtp.gmail.com`
- `GmailAuthenticationException` wrap các lỗi auth

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu gốc ở trên được giữ nguyên. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Thiết kế hiện tại:** `MailCredentials.forMethod(OAUTH2)` lấy token hợp lệ qua AccessTokenProvider/OAuth2Service, dùng SASL XOAUTH2 cho IMAPS/SMTP, tắt LOGIN/PLAIN ở OAuth2. Wrapper/timeout/TLS như AM-4358. OAuth2 bị từ chối -> ReauthRequiredException/422 GMAIL_NEEDS_REAUTH; nếu có App Password dự phòng thì fallback một lần, giữ NEEDS_REAUTH và audit FALLBACK_TO_APP_PASSWORD. Không retry lỗi auth, không log token. Caller kiểm tra ownership/feature.
- **Code:** `Mail Server/src/main/java/com/mailapp/mail/model/MailCredentials.java#forMethod`, `mail/DefaultAccessTokenProvider.java`, `mail/DefaultMailConnectionFactory.java#doOpenImap`, `#doOpenSmtp`, `#executeWithFallback`, `#mapException`, `oauth2/OAuth2ServiceImpl.java#getFreshAccessToken`. Không dùng OAuth2SaslClientFactory riêng hay Gmail REST API.
- **Test có trong repo:** `XOAuth2IntegrationTest` (mock token provider và chuỗi SASL; test tên session properties chỉ kiểm tra factory khởi tạo, không chứng minh handshake), `AccessTokenProviderTest`, `MailFallbackConnectionTest`; bổ sung `RealGmailXOAuth2IntegrationTest#authenticatesImapsAndSmtpWithRealXOAuth2` gated bằng opt-in + email/token. Kết quả chạy tại §5; XOAUTH2 Google thật cần token mail scope còn hạn và được người dùng cấp an toàn (AM-4361).

- **Đối chiếu bổ sung:** Cập nhật adapter gốc dùng `createSession` + XOAUTH2, TLS xác minh hostname; raw Store/Transport chưa connect và caller close. Test `legacyOAuthSessionEnforcesXoauth2TlsAndScopedAuthenticator` kiểm properties, provider và Authenticator không cấp credential sang khác host/protocol/user; đã chạy, xem XML §5. Preferred method được dùng khi chuẩn bị raw Session; open* vẫn là API thực thi fallback/retry đầy đủ. Rà N/A: ownership/flag/audit riêng không áp dụng ở low-level factory, nhưng caller và fallback audit vẫn bắt buộc. Google XOAUTH2 thật chưa xác minh.

- **Bổ sung vòng G2 (sau F):** OAuth2MockWebServerTest 21/21, MailFallbackConnectionTest 3/3, XOAuth2IntegrationTest 3/3, AccessTokenProviderTest 2/2. Exact grant cache, rotated refresh, reduced scope deny, repeated rejected grant không refresh, fallback fail không báo active/audit thành công, explicit APP_PASSWORD usable khi dual. Hai live XOAUTH2 test SKIP. Không thay manual/live.

- **Bổ sung G3:** MailboxRevisionChainIntegrationTest 2/2 kiểm commit rotation, hai revision liên tiếp invalid_grant/fallback, one audit/action, stale grant/provider deny và Store close. OAuth local regression PASS, không phải XOAUTH2 live.

- **Bổ sung G4:** G4 không lặp refresh/fallback; chỉ dùng evidence G2/G3 và raw App Password scope; remote XOAUTH2/consent vẫn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 2 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AccessTokenProviderTest: 2/2 PASS; MailConnectionFactoryTest: 5/5 PASS; MailFallbackConnectionTest: 3/3 PASS; RealGmailXOAuth2IntegrationTest: 0/2 PASS, 2 SKIP (2 SKIPPED); XOAuth2IntegrationTest: 3/3 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Raw Session/Store/Transport adapter XOAUTH2 + scoped authenticator, token refresh/fallback và TLS. XOAuth2IntegrationTest/AccessTokenProviderTest/MailFallbackConnectionTest đạt; provider exchange/revoke loopback đạt, handshake IMAPS/SMTP Google thật vẫn OPEN ở AM-4361.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Factory XOAUTH2 cấp kết nối; caller kiểm owner/flag/audit, fallback vẫn ghi audit riêng.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Factory XOAUTH2 cấp kết nối; caller kiểm owner/flag/audit, fallback vẫn ghi audit riêng.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Factory XOAUTH2 cấp kết nối; caller kiểm owner/flag/audit, fallback vẫn ghi audit riêng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4360 (H refs trong hàng AM-4360, PARTIAL): H051–059 OAuth local link/relink, H060 unlink, H062–065 link lại; counters loopback được báo. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G023–040, G064–066 và G090: OAuth loopback thiếu scope/profile mismatch/replay/expiry bị từ chối, recovery SUCCESS; không phải handshake XOAUTH2 tới Gmail. Phần còn lại tại mốc trước F: Còn consent/token refresh/XOAUTH2 live, wrong scope/auth recovery thực được phép. Tách riêng human sign-off, live, contract và kết quả tự động. Bổ sung A/B và regression cuối: [backend-supplemental-observations.md](../backend-supplemental-observations.md) — Lifecycle pool phân tách mailbox/method/secret fingerprint; persistence failure sau open đóng lease. Regression tự động không thay Google consent, token refresh hoặc XOAUTH2 live. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): F218–225: local PKCE authorize/callback/poll thành công, grant hết hạn và refresh thực bị invalid_grant (refreshRejected=1); folders422 GMAIL_NEEDS_REAUTH, account NEEDS_REAUTH. Không chứng minh refresh thành công, fallback, real XOAUTH2 IMAPS/SMTP hoặc consent Google. **Còn thiếu hiện tại:** Consent, refresh thành công/fallback và XOAUTH2 IMAPS/SMTP/wrong-scope recovery thật; local refresh invalid_grant và persisted NEEDS_REAUTH đã có F218–225. Không thay trạng thái checkbox. **Vòng G2:** OAuth2MockWebServerTest 21/21, MailFallbackConnectionTest 3/3, XOAuth2IntegrationTest 3/3, AccessTokenProviderTest 2/2. Exact grant cache, rotated refresh, reduced scope deny, repeated rejected grant không refresh, fallback fail không báo active/audit thành công, explicit APP_PASSWORD usable khi dual. Hai live XOAUTH2 test SKIP. Không thay manual/live. **Còn thiếu:** Successful refresh, rotated/reduced scope, invalid_grant suppression, fallback fail/active và XOAUTH2 local đã có automated; F invalid_grant local quan sát trực tiếp đã có. Còn manual flow được phép và consent/refresh/fallback/XOAUTH2/remote wrong-scope recovery thật. Không đổi checkbox. **Vòng G3:** MailboxRevisionChainIntegrationTest 2/2 kiểm commit rotation, hai revision liên tiếp invalid_grant/fallback, one audit/action, stale grant/provider deny và Store close. OAuth local regression PASS, không phải XOAUTH2 live. Không đổi checkbox. **G4:** G4 không lặp refresh/fallback; chỉ dùng evidence G2/G3 và raw App Password scope; remote XOAUTH2/consent vẫn thiếu. [AM-4360-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4360-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M07] MailConnectionFactory — kết nối Gmail dùng chung**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
