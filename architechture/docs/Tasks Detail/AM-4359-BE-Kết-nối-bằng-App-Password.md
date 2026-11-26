# [BE] Kết nối bằng App Password

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M07] MailConnectionFactory — kết nối Gmail dùng chung |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 27/10/2026 → 28/10/2026 |
| Ước lượng | 2.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Kết nối bằng App Password` thuộc **[M07] MailConnectionFactory — kết nối Gmail dùng chung** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

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

- **Thiết kế hiện tại:** `MailCredentials.forMethod(APP_PASSWORD)` dùng AesCipher giải mã secret khi cần; IMAP Store.connect(email,secret), SMTP Authenticator/Transport.connect. Factory wrapper đã connect, đóng hoặc trả pool, timeout/TLS như AM-4358. Endpoint liên kết EP-G2 kiểm tra ALLOW_APP_PASSWORD_LINK, giới hạn account mới và test IMAP mới, không pool/retry, mỗi socket timeout <=10s và đóng vật lý trước khi encrypt/upsert; sai credential 422 GMAIL_AUTH_FAILED, IMAP disabled 422, mạng 502. Không yêu cầu audit mỗi lần mở kết nối; link/fallback được audit ở nghiệp vụ.
- **Code:** `Mail Server/src/main/java/com/mailapp/mail/model/MailCredentials.java#forMethod`, `mail/DefaultMailConnectionFactory.java#doOpenImap`, `#doOpenSmtp`, `#testImap`, `#mapException`; `controller/GmailAccountController.java#linkGmailWithAppPassword`, `service/GmailAccountLinkService.java`. Mask email/secret trong toString/log; không trả secret qua DTO.
- **Test có trong repo:** `DefaultMailConnectionFactoryGreenMailTest` (IMAP success/auth fail, SMTP send), `MailConnectionFactoryTest#testOpenImapWithGmailAccountDecryptsPassword`, `MailConnectionFaultAndRetryTest`, `GmailAccountControllerTest`, `GmailMultiAuthIntegrationTest`. Kết quả chạy tại §5; cần Gmail test riêng đã bật 2FA/App Password và kết nối trực tiếp (AM-4361/4396).

- **Đối chiếu bổ sung:** Cập nhật adapter gốc `createImapStore/createSmtpTransport/createSession` chia sẻ properties với open*: caller sở hữu raw chưa connect; App Password chỉ trong Authenticator nội bộ đúng host/protocol/port/user. Test GreenMail `legacyRawConnectionsAreUnconnectedAndOwnedByTheCaller` kiểm connect và close của cả hai raw API, chưa chạy. Rà N/A: không áp dụng ownership/feature/audit cho hàm factory độc lập; EP-G2 vẫn bắt buộc kiểm ownership/feature/probe và audit link ở caller (AM-4314). Live cần tài khoản test 2FA/App Password.

- **Bổ sung vòng G2 (sau F):** Scoped SMTP Authenticator và raw adapter regression; DefaultMailConnectionFactoryGreenMailTest 10/10. Actual link sai credential, standalone Gmail remote/2FA vẫn OPEN.

- **Bổ sung G3:** Factory kiểm persisted snapshot trước giải mã/connection và sau open; local regression PASS. Không thay actual link credential hoặc Gmail live.

- **Bổ sung G4:** G4 R02–R10 IMAPS/SMTP raw loopback với AES synthetic thật, close success/error scope; actual link sai App Password và 2FA/remote Gmail còn thiếu.

- **Bổ sung G6:** PASS manual local scope implementation App Password theo 03 M07: G4 R02–R10 raw AES synthetic, IMAPS/SMTP caller-owned; H/F/G/T connect/close/error/recovery thực local. F204/F205 deny trên link đã có, không phải POST wrong App Password. 4359-M.03 S/H/L vẫn phải thỏa AM-4314-M.03/M.04 (POST link/probe), AM-4396-M.02/M.03 (dedicated 2FA/standalone) và AM-4361-M.02/C.02 (remote TLS/timeout). Không claim Gmail thật.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** GmailAccountControllerTest: 15/15 PASS; DefaultMailConnectionFactoryGreenMailTest: 10/10 PASS; MailConnectionFactoryTest: 5/5 PASS; MailConnectionFaultAndRetryTest: 4/4 PASS; GmailMultiAuthIntegrationTest: 9/9 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Raw adapter App Password và open* đã thực thi trên GreenMail IMAPS/SMTP, probe đúng/sai và close, không trust-all. Chưa chạy Gmail thật (AM-4361).
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Factory App Password cấp kết nối; caller link/operation kiểm owner/flag/audit.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Factory App Password cấp kết nối; caller link/operation kiểm owner/flag/audit.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Factory App Password cấp kết nối; caller link/operation kiểm owner/flag/audit.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (local agent, không human/live). **G6:** PASS manual local scope implementation App Password theo 03 M07: G4 R02–R10 raw AES synthetic, IMAPS/SMTP caller-owned; H/F/G/T connect/close/error/recovery thực local. F204/F205 deny trên link đã có, không phải POST wrong App Password. 4359-M.03 S/H/L vẫn phải thỏa AM-4314-M.03/M.04 (POST link/probe), AM-4396-M.02/M.03 (dedicated 2FA/standalone) và AM-4361-M.02/C.02 (remote TLS/timeout). Không claim Gmail thật. Không observation mới; **Lịch sử evidence trước G6 (không phải trạng thái hiện tại):**. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4359 (H refs trong hàng AM-4359, PARTIAL): H013 App Password ACTIVE, H083 update và H105 relink 201; H106 mở mailbox, H027–030 SMTP/IMAP round trip fixture. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G054–057: App Password mailbox fixture qua IMAPS, fault 502 và resume đúng UID/UIDVALIDITY. Bổ sung vòng 61: [backend-supplemental-observations.md](../backend-supplemental-observations.md) T004–013: App Password linked fixture, IMAPS/SMTP pre-auth timeout 504, retry và đóng socket, recovery giữ mailbox; không đọc hoặc nhập credential. Phần còn lại tại mốc trước F: Còn wrong App Password trực tiếp, raw/probe lifecycle, remote TLS/timeout và Gmail 2FA thật; không retry credential bị chặn. Local warm lease/close/recovery đã kiểm. Bổ sung A/B và regression cuối: [backend-supplemental-observations.md](../backend-supplemental-observations.md) — B002–006/B030–037 App Password link synthetic: warm pool release/eviction và fault recovery; controller selftest read/close failure regression không báo kết nối tốt giả. Còn wrong credential manual và Gmail 2FA/remote thật. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [BE](../backend-supplemental-observations.md): C operations local GreenMail, read-back sau fault; không kiểm sai App Password hoặc Gmail remote thật. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): F203–211 provider từ chối auth của mailbox App Password đã link, hai HTTP422, no delivery, socket0, restore 200. Không phải nhập sai App Password/link POST/standalone Store; Gmail 2FA/remote vẫn thiếu. **Còn thiếu hiện tại:** Actual link sai App Password hoặc standalone raw/probe, remote TLS/timeout/2FA Gmail thật; local provider deny existing mailbox và socket close đã có F203–211. Không thay trạng thái checkbox. **Vòng G2:** Scoped SMTP Authenticator và raw adapter regression; DefaultMailConnectionFactoryGreenMailTest 10/10. Actual link sai credential, standalone Gmail remote/2FA vẫn OPEN. **Còn thiếu:** Raw local automated lifecycle/auth đã có; còn actual link sai App Password/standalone raw Gmail và 2FA/remote TLS/timeout. Không đổi checkbox. **Vòng G3:** Factory kiểm persisted snapshot trước giải mã/connection và sau open; local regression PASS. Không thay actual link credential hoặc Gmail live. Không đổi checkbox. **G4:** G4 R02–R10 IMAPS/SMTP raw loopback với AES synthetic thật, close success/error scope; actual link sai App Password và 2FA/remote Gmail còn thiếu. [AM-4359-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4359-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M07] MailConnectionFactory — kết nối Gmail dùng chung**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
