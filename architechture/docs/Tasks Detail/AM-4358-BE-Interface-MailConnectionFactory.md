# [BE] Interface MailConnectionFactory

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M07] MailConnectionFactory — kết nối Gmail dùng chung |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 26/10/2026 → 26/10/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Interface MailConnectionFactory` thuộc **[M07] MailConnectionFactory — kết nối Gmail dùng chung** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

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

- **Thiết kế hiện tại:** interface `openImap/openSmtp` nhận GmailAccount hoặc MailCredentials, trả `ImapConnection/SmtpConnection` đã connect và AutoCloseable; `testImap` trả ConnectionTestResult. Thêm adapter public createSession/createImapStore/createSmtpTransport cho caller nội bộ có lifecycle tường minh. Dùng Jakarta Mail (Spring Boot 3); IMAPS 993 và SMTP STARTTLS bắt buộc 587 tới remote, TLS 1.2/1.3, xác minh hostname/truststore (không ép trust bỏ xác minh). Chỉ GreenMail opt-in local-test-transport và loopback được nới TLS cho fixture, không trust-all. Mặc định connect/read/write 10/15/30 giây, override qua properties; probe mới không pool/retry, mỗi socket timeout tối đa 10s. Phân loại MailAuthException/ReauthRequiredException/MailNetworkException (422/422/502 hoặc 504).
- **Code:** `Mail Server/src/main/java/com/mailapp/mail/MailConnectionFactory.java`, `DefaultMailConnectionFactory.java#openImap`, `#openSmtp`, `#doOpenImap`, `#doOpenSmtp`, `#mapException`; `mail/config/MailConnectionProperties.java`, `mail/model/MailCredentials.java`, `ImapConnection.java`, `SmtpConnection.java`, `src/main/resources/application.properties`. Factory nhận account đã được caller xác thực ownership/flag; chỉ audit fallback khi có chuyển phương thức, không log credential.
- **Test có trong repo:** `DefaultMailConnectionFactoryGreenMailTest`, `MailConnectionFactoryTest`, `MailConnectionFaultAndRetryTest`, `MailFallbackConnectionTest`, `SecretMaskingTest`. Kết quả chạy tại §5; test localhost không chứng minh kết nối TLS tới Gmail thật, xem AM-4361. `MailConnectionFactoryTest#factoryDefaultsAndProbeTimeoutMatchTaskAndTestTransportRequiresOptIn` kiểm timeout và test transport. Kết quả chạy tại §5.

- **Đối chiếu bổ sung:** Cập nhật Java API gốc: thêm `MailConnectionFactory.Protocol`, `createSession`, `createImapStore`, `createSmtpTransport`; raw Store/Transport trả chưa connect, không thuộc pool, caller connect và close bằng try-with-resources. Cùng IMAPS/SMTP TLS/timeout/auth props, Authenticator chỉ cấp credential theo host/protocol/port/user, không đưa secret vào Properties. Giữ `open*` cho nghiệp vụ để thực thi fallback/retry/phân loại exception; raw adapter chỉ chuẩn bị preferred method, không suy diễn login thành công trước handshake. `DefaultMailConnectionFactoryGreenMailTest#legacyRawConnectionsAreUnconnectedAndOwnedByTheCaller`, `MailConnectionFactoryTest#legacyOAuthSessionEnforcesXoauth2TlsAndScopedAuthenticator` mới, chưa chạy. Tên exception hiện hành MailAuthException/ReauthRequiredException giữ status/code 422; không thêm raw token getter vào factory (AccessTokenProvider nội bộ). Rà N/A: ownership/flag/audit riêng của factory không áp dụng; caller phải xác minh quyền/flag, fallback vẫn audit.

- **Bổ sung vòng G2 (sau F):** Raw Store/Transport local caller-owned test đã chạy 10/10; scoped Authenticator tái sử dụng cho SMTP wrapper, fallback chỉ báo active sau mở thật và đóng resource khi persist conflict. MailConnectionFactoryTest 5/5, MailConnectionFaultAndRetryTest 4/4. Chưa manual lifecycle/remote TLS.

- **Bổ sung G3:** MailboxRevisionChainIntegrationTest 2/2 với Store đóng sau fallback; stale raw Store/Transport bị từ chối, wrapper recheck sau open. Manual/remote TLS vẫn thiếu.

- **Bổ sung G4:** G4 R02–R10 hoàn tất raw lifecycle local, Folder đóng độc lập, Store/Transport đóng cả nhánh lỗi scope; còn hostname/TLS/timeout/2 mode Gmail thật.

- **Bổ sung G6:** PASS manual local scope interface: 03 M07 tách Interface MailConnectionFactory, implementation App Password, XOAUTH2 và Test factory kết nối thật; 02 C3 chốt interface sớm. G4 R02–R10 actual raw Store/Transport caller-owned connect/close và lỗi scope; H/B/T/F wrapper HTTP/lease/timeout; config có code/test. CLI không gọi là Postman. 4358-M.04 S/L remote hostname/TLS/timeout và hai auth mode vẫn phải thỏa AM-4361-M.02/C.02.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** DefaultMailConnectionFactoryGreenMailTest: 10/10 PASS; MailConnectionFactoryTest: 5/5 PASS; MailConnectionFaultAndRetryTest: 4/4 PASS; MailFallbackConnectionTest: 3/3 PASS; SecretMaskingTest: 6/6 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Khôi phục createImapStore/createSmtpTransport/createSession; raw chưa connect, caller đóng; open* AutoCloseable giữ retry/fallback. Factory tests đạt TLS/timeout 10/15/30s và phân vùng auth; remote Gmail chưa xác minh.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Factory cấp kết nối, caller kiểm quyền/feature/audit; fallback có audit ở luồng thực thi.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Factory cấp kết nối, caller kiểm quyền/feature/audit; fallback có audit ở luồng thực thi.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Factory cấp kết nối, caller kiểm quyền/feature/audit; fallback có audit ở luồng thực thi.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (local agent, không human/live). **G6:** PASS manual local scope interface: 03 M07 tách Interface MailConnectionFactory, implementation App Password, XOAUTH2 và Test factory kết nối thật; 02 C3 chốt interface sớm. G4 R02–R10 actual raw Store/Transport caller-owned connect/close và lỗi scope; H/B/T/F wrapper HTTP/lease/timeout; config có code/test. CLI không gọi là Postman. 4358-M.04 S/L remote hostname/TLS/timeout và hai auth mode vẫn phải thỏa AM-4361-M.02/C.02. Không observation mới; **Lịch sử evidence trước G6 (không phải trạng thái hiện tại):**. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4358 (H refs trong hàng AM-4358, PARTIAL): H015/H016 và H027–030 IMAP/SMTP hoạt động qua factory trong fixture, API gửi nhận thực. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G054–063: IMAPS/SMTP listener fault trước connect trả 502, phục hồi đúng marker, không reset mailbox. Bổ sung vòng 61: [backend-supplemental-observations.md](../backend-supplemental-observations.md) T004–013: timeout 504 IMAPS/SMTP, fixture 2 s và 2 attempts; từng protocol accepted2/peerClosed2/fixtureClosed0/active0; recovery giữ mailbox, không phát sinh thư. Phần còn lại tại mốc trước F: Còn raw Store/Transport lifecycle và remote hostname/TLS/timeout với Gmail thật; local successful pooled lease, unpooled SMTP close, idle/broken và recovery đã kiểm. Bổ sung A/B và regression cuối: [backend-supplemental-observations.md](../backend-supplemental-observations.md) — B002–006/B030–037 và regression healthyLeaseReturnsOnceBrokenLeaseClosesAndIdleStoreExpires/poolCapacityAndAccountIsolation: release logical, idle eviction, broken discard, warm outage/recovery thật; SMTP socket 0 sau send. Chưa quan sát toàn bộ raw API lifecycle và remote Gmail TLS. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [BE](../backend-supplemental-observations.md): C012 đo socket trước restart IMAPS1/SMTP0; lỗi audit/outbox không mở thêm mail lease qua replay. B evidence idle/broken/recovery kế thừa, không lặp. Raw Store/Transport và remote Gmail chưa quan sát. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): F204/F205 provider auth bị từ chối và F207/F217 owned sockets0; F209 recovery. Bổ sung open* wrapper lifecycle; không chứng minh raw Store/Transport caller-owned, remote hostname/TLS Gmail. Không thay trạng thái checkbox. **Vòng G2:** Raw Store/Transport local caller-owned test đã chạy 10/10; scoped Authenticator tái sử dụng cho SMTP wrapper, fallback chỉ báo active sau mở thật và đóng resource khi persist conflict. MailConnectionFactoryTest 5/5, MailConnectionFaultAndRetryTest 4/4. Chưa manual lifecycle/remote TLS. **Còn thiếu:** Đã có automated raw Store/Transport lifecycle caller-owned local và factory regression; còn thao tác manual được phép, Gmail hostname/TLS/timeout remote thật. Không đổi checkbox. **Vòng G3:** MailboxRevisionChainIntegrationTest 2/2 với Store đóng sau fallback; stale raw Store/Transport bị từ chối, wrapper recheck sau open. Manual/remote TLS vẫn thiếu. Không đổi checkbox. **G4:** G4 R02–R10 hoàn tất raw lifecycle local, Folder đóng độc lập, Store/Transport đóng cả nhánh lỗi scope; còn hostname/TLS/timeout/2 mode Gmail thật. [AM-4358-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4358-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M07] MailConnectionFactory — kết nối Gmail dùng chung**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
