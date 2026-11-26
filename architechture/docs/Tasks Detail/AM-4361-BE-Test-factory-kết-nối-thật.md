# [BE] Test factory kết nối thật

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M07] MailConnectionFactory — kết nối Gmail dùng chung |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 28/10/2026 → 29/10/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Test factory kết nối thật` thuộc **[M07] MailConnectionFactory — kết nối Gmail dùng chung** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

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

- **Bằng chứng mã hiện tại:** RealGmailIntegrationTest có test IMAPS/INBOX/resolver và test SMTP self-send attachment/hash/targeted Trash cleanup. Chỉ kiểm thư synthetic đánh dấu riêng, không đọc subject thư đã có. Gated bằng dedicated TEST-ACCOUNT và opt-in live cùng biến cấu hình được cấp phép; chưa chạy.
- **XOAUTH2:** RealGmailXOAuth2IntegrationTest có authenticatesImapsAndSmtpWithRealXOAuth2 và sendsAndDownloadsSyntheticAttachmentWithRealXOAuth2, cùng lifecycle synthetic self-send/hash/targeted cleanup; opt-in dedicated test account, chưa chạy. Local GreenMail/mock không thay live.
- **Còn thiếu xác minh:** không có bằng chứng tài khoản test, 2FA, App Password, token thật đã được cấp/chạy trong đợt này; không đánh dấu hoàn thành live/manual. Dùng hộp thư test riêng và không commit/in credential. Chạy offline phải bỏ biến kích hoạt live. Không có endpoint/audit nghiệp vụ riêng của task factory test; ownership/flag kiểm tra ở caller. Kết quả suite do người phụ trách chạy test ghi riêng.

- **An toàn live test:** cần opt-in `MAILAPP_REAL_GMAIL=1` và Gmail test riêng. Script test-real-gmail.ps1 yêu cầu xác nhận TEST-ACCOUNT; chỉ expunge đúng UID thư test. Thiết lập theo `Mail Server/docs/dev-setup.md` và placeholder `.env.example`.

- **Đối chiếu bổ sung:** Rà N/A: task test factory độc lập không có endpoint/user mutation/feature flag/audit riêng; nghiệp vụ sử dụng factory vẫn có ownership, feature và audit. Thiếu opt-in/tài khoản test riêng được cấp; App Password test yêu cầu MAILAPP_REAL_GMAIL=1 + GMAIL_TEST_EMAIL/GMAIL_TEST_APP_PASSWORD, OAuth2 cần MAILAPP_REAL_GMAIL_XOAUTH2=1 + email/token mail scope còn hạn. Không chạy live, không lấy credential, không suy ra kết quả từ GreenMail.

- **Bổ sung vòng G2 (sau F):** Raw local factory và local refresh/fallback/XOAUTH2 regression; repo có hai live App Password tests và hai live XOAUTH2 tests gồm handshake + lifecycle synthetic self-send/hash/targeted cleanup. Không chạy live; contract/privacy/manual vẫn OPEN.

- **Bổ sung G3:** G3 local revision chain và connection lifecycle PASS; 5 live SKIP kế thừa; không nghiệm thu thật.

- **Bổ sung G4:** G4 raw local R02–R10, live opt-in App Password bổ sung standalone raw Store/Transport source, compile thành công nhưng chưa chạy live.

- **Bổ sung G6:** Giữ shared 4320-M.05, 4358-M.04, 4359-M.03: remote hostname/TLS/timeout cả hai auth mode, special-use/count, delivery/read-back/hash/targeted cleanup. Manual/contract/privacy live vẫn OPEN.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 0 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 3 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** RealGmailIntegrationTest: 0/2 PASS, 2 SKIP (2 SKIPPED); RealGmailXOAuth2IntegrationTest: 0/2 PASS, 2 SKIP (2 SKIPPED); XOAuth2IntegrationTest: 3/3 PASS; SecretMaskingTest: 6/6 PASS; RealGmailMimeFixturesTest: 0/1 PASS, 1 SKIP (1 SKIPPED).

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec — OPEN. Opt-in dedicated Gmail test App Password/XOAUTH2 kiểm kết nối và self-send/hash/targeted UID cleanup. RealGmailIntegrationTest 2/2 SKIP; RealGmailXOAuth2IntegrationTest 2/2 SKIP; RealGmailMimeFixturesTest 1/1 SKIP. Fixture không chứng minh handshake thật; cần tài khoản test riêng được cấp phép và cả hai auth mode. **G4:** G4 raw local R02–R10, live opt-in App Password bổ sung standalone raw Store/Transport source, compile thành công nhưng chưa chạy live. [AM-4361-C ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4361-c).
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Task kiểm thử kết nối trực tiếp fixture/Gmail, không endpoint owner/flag/audit riêng.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Task kiểm thử kết nối trực tiếp fixture/Gmail, không endpoint owner/flag/audit riêng.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Task kiểm thử kết nối trực tiếp fixture/Gmail, không endpoint owner/flag/audit riêng.
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log — OPEN. Dedicated live opt-in, masking/safe-stage, không đọc thư tùy ý; chỉ thư synthetic marker/UID, không in secret, không xuất raw To/subject/body. RealGmailIntegrationTest 2/2 SKIP; RealGmailXOAuth2IntegrationTest 2/2 SKIP; RealGmailMimeFixturesTest 1/1 SKIP. Chưa quan sát response/log và quyền trên Gmail test thật; fixture/code không thay thế kiểm chứng live. **G4:** G4 raw local R02–R10, live opt-in App Password bổ sung standalone raw Store/Transport source, compile thành công nhưng chưa chạy live. [AM-4361-P ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4361-p).
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4361 (không có H trực tiếp cho hành vi trọng tâm, BLOCKED LIVE): Chỉ quan sát GreenMail và OAuth loopback. Không có kết nối IMAPS/SMTP Google thật trong snapshot. Phần còn lại: Tài khoản Gmail test riêng, App Password 2FA và access token full mail scope, chạy opt-in, 0 skip. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [backend-supplemental-observations.md](../backend-supplemental-observations.md) — Cùng bộ mẫu MIME live được rehearsal local SMTP/IMAPS, kiểm 7 mẫu và exact bytes qua DefaultMailConnectionFactoryGreenMailTest. Năm live test vẫn SKIP; không suy thành Gmail auth thật. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): F204/F205/F213/F222 local protocol faults và F226 canary0; vẫn thiếu cả hai auth mode Gmail thật, self-send/hash/read-back/targeted cleanup và privacy live. Không đổi contract/privacy/manual. Không thay trạng thái checkbox. **Vòng G2:** Raw local factory và local refresh/fallback/XOAUTH2 regression; repo có hai live App Password tests và hai live XOAUTH2 tests gồm handshake + lifecycle synthetic self-send/hash/targeted cleanup. Không chạy live; contract/privacy/manual vẫn OPEN. Không đổi checkbox. **Vòng G3:** G3 local revision chain và connection lifecycle PASS; 5 live SKIP kế thừa; không nghiệm thu thật. Không đổi checkbox. **G4:** G4 raw local R02–R10, live opt-in App Password bổ sung standalone raw Store/Transport source, compile thành công nhưng chưa chạy live. [AM-4361-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4361-m). **G6:** Giữ shared 4320-M.05, 4358-M.04, 4359-M.03: remote hostname/TLS/timeout cả hai auth mode, special-use/count, delivery/read-back/hash/targeted cleanup. Manual/contract/privacy live vẫn OPEN.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M07] MailConnectionFactory — kết nối Gmail dùng chung**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
