# [BE] Gửi mail SMTP multipart

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M10] Gửi mail — SMTP |
| Owner | TV5 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 03/11/2026 → 06/11/2026 |
| Ước lượng | 8h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Gửi mail SMTP multipart` thuộc **[M10] Gửi mail — SMTP** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M5 POST /api/mails/send`

- **Path:** `/api/mails/send`
- **Method:** POST
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Gửi mail SMTP STARTTLS multipart (to,cc,bcc,subject,body,isHtml,files[]); kiểm tra ALLOW_SEND_MAIL; log SEND_MAIL.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

> **Điều chỉnh đặc tả 26/11/2026 — chủ dự án đã phê duyệt:** Sau đối chiếu ba khác biệt contract, chủ dự án yêu cầu sửa Task Detail theo phương án hiện hành. §4 dưới đây là yêu cầu áp dụng; ghi chú/evidence G6 trở về trước là lịch sử. Phê duyệt không thay manual/live evidence hoặc giảm guard bảo mật.

POST /api/mails/send (multipart/form-data)
- Fields: `gmailAccountId`, `to`, `cc`, `bcc`, `subject`, `body`, `isHtml` (bool), `files[]` (MultipartFile 0..n)
- Verify ownership + check `ALLOW_SEND_MAIL` → 403 FEATURE_DISABLED
- Validate: `to` email hợp lệ, `subject` ≤ 200 chars, `body` ≤ 1MB
- Từng file: `file.getSize() > MAX_ATTACHMENT_SIZE_MB*1024*1024` → 413 PAYLOAD_TOO_LARGE
- Dựng `MimeMessage`:
  - `setFrom(acc.email)`, `setRecipients(TO/CC/BCC)`, `setSubject(subject,"UTF-8")`
  - Nếu có files: `Multipart` = `MimeBodyPart(body)` + `MimeBodyPart(file)` mỗi file (`attachFile`, `MimeUtility.encodeText(filename,"UTF-8","B")`)
  - Else: `setContent(body, isHtml?"text/html; charset=utf-8":"text/plain; charset=utf-8")`
- `MailConnectionFactory.createSmtpTransport(acc)` → `transport.connect()` → `transport.sendMessage(msg, msg.getAllRecipients())` → close
- Log `SEND_MAIL` sau thành công: metadata gồm gmailId, email nguồn đã che, recipientCount, toCount/ccCount/bccCount và attachmentCount; không lưu raw To/CC/BCC/subject/body, credential hoặc hash của nội dung nhạy cảm. Không ghi audit thành công khi outcome chưa xác định, không tự retry gửi.
- Response 200 `{message:"Sent", sentAt}`
- Errors: auth → 422; network → 502 GMAIL_CONNECTION_ERROR; address sai → 400 với message từ `SendFailedException`

> **Lịch sử contract:** §4 trước phê duyệt yêu cầu SEND_MAIL lưu `{to, subject, attachmentCount}` (không body). Phương án metadata-only ở trên thay thế đúng yêu cầu đó, không đổi chức năng gửi, multipart, ownership, validation hay yêu cầu Gmail live.

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu hiện hành là §4 đã được phê duyệt 26/11/2026; các đối chiếu trước phê duyệt bên dưới được giữ làm lịch sử. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Contract hiện tại:** multipart/form-data với part JSON `payload` chứa `gmailId` (alias gmailAccountId), to/cc/bcc dạng mảng, subject, textBody/htmlBody, inReplyTo/references; part `attachments` nhiều file. Cùng endpoint nhận form gốc `gmailAccountId,to,cc,bcc,subject,body,isHtml,files/files[]`, chuyển sang cùng DTO/validation; không trộn JSON payload với trường form text. Response `200 {messageId,status:"SENT",message:"Sent",sentAt}`. Giới hạn subject 200 code point, tổng UTF-8 textBody+htmlBody 1 MiB, từng file <= MAX_ATTACHMENT_SIZE_MB, tổng file <= 60 MiB, tối đa 50 người nhận, chống header injection/rate limit. SMTP invalid address trả 400 INVALID_EMAIL thông báo chung, tránh lộ địa chỉ; auth App Password 422, OAuth2 cần reauth 422, network 502/504.
- **Code:** `Mail Server/src/main/java/com/mailapp/controller/MailController.java#sendMail`, `mail/service/MailServiceImpl.java#sendMail` và `#validateSendContent`, `MailMessageBuilder.java` (MIME UTF-8, alternative/mixed, MultipartFile DataSource stream), `MailHeaderAndRateGuard.java`, `HtmlSanitizerService.java#sanitizeOutgoing`, `mail/SmtpConnection.java`. Ownership/bảo trì và ALLOW_SEND_MAIL trước gửi; audit SEND_MAIL sau thành công ghi gmailId, email nguồn đã che, recipientCount, attachmentCount và IP, không to/subject/body (contract tăng riêng tư).
- **Test có trong repo:** `MailMessageBuilderTest`, `MailServiceGreenMailIntegrationTest#testSendSearchPaginateFlagsAndDeleteLifecycle`, `#rejectsOversizedSubjectAndBodyBeforeSending`, `#testSecurityGuards`, `MailServiceFailureRegressionTest#rejectedSmtpRecipientIsBadRequestWithoutAddressLeakOrSuccessAudit`, `MailSecurityAndSanitizerTest`. Kết quả chạy tại §5; cần manual multipart Postman, gửi/nhận thật với Gmail test và xác minh audit DB. `MailControllerCompatibilityTest` kiểm cả JSON multipart, form legacy, file aliases, validation và 413.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Đối chiếu bổ sung:** Cập nhật: MAX_ATTACHMENT_SIZE_MB áp dụng từng file theo §4 và thiết kế tổng thể, cả JSON/form; giữ giới hạn HTTP request 60 MiB và từng part 50 MiB, service chặn tổng file >60 MiB (kể cả direct caller). Swing giữ tổng theo AM-4368. `MailServiceFailureRegressionTest#smtpPerFileLimitAllowsSeveralFilesAndAuditOmitsSensitiveHeaders` kiểm hai file hợp lệ có tổng vượt setting, file riêng vượt setting trả 413 trước SMTP, audit đúng số đính kèm/người nhận và không lộ to/subject/body. Giữ quyết định privacy: §4 yêu cầu to/subject nhưng acceptance cấm lộ nội dung và audit toàn cục Admin; không lưu trường nhạy cảm, không khẳng định khớp literal audit detail. Cần chủ spec xác nhận metadata thay thế và manual Gmail/Postman. Không có N/A trong task.

- **Bổ sung vòng G2 (sau F):** SMTP Authenticator của wrapper dùng cùng phạm vi host/protocol/port/user như raw adapter; GreenMail 10/10, MailServiceFailureRegressionTest 22/22. Không đổi journal, không retry external mutation, không ghi To/subject. Raw Transport manual/live vẫn thiếu.

- **Bổ sung G3:** G3 guard raw/wrapper Transport trước cấp kết nối và sau open, không thay SMTP journal hoặc retry mutation. Giữ F post-delivery unknown, raw manual/Gmail live và contract privacy riêng.

- **Bổ sung G4:** G4 R06–R08/R10 hoàn tất raw Transport create/connect/close và abort phía caller trước send. Không SMTP DATA fault mới, không retry; còn Gmail thật và quyết định contract metadata.

- **Bổ sung G6:** PASS manual local multipart đúng hành vi đang triển khai: H027/H040/H042/H120/H136–140, F013/F014/F064–065/F069–070, G045–063/G079–080, J001–005/J009–011, T009–013, F205/F213–217, C/D/E, G4 R06–R08/R10. Không tính setup H036 hoặc hash lỗi trước fix. Có direct HTTP từng request (không đổi nhãn thành Postman), delivery/read-back/bytes local và no retry khi uncertain. Giữ 4323-M.05 S/L trong 4323-C.04: Gmail thật cả hai auth mode, delivery/read-back/attachment, TV5. Contract 4323-C.03 D về audit metadata-only vẫn OPEN, không phê duyệt raw To/subject.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailControllerCompatibilityTest: 12/12 PASS; MailMessageBuilderTest: 5/5 PASS; MailSecurityAndSanitizerTest: 3/3 PASS; MailServiceFailureRegressionTest: 22/22 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS; SecretMaskingTest: 6/6 PASS; MailAuditRecoveryIntegrationTest: 13/13 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec — OPEN. **Phê duyệt 26/11/2026:** 4323-C.03 đã đạt theo §4 điều chỉnh: SEND_MAIL metadata-only, masked sender/counts, không raw/hash To/subject/body. Evidence local H093/F013–014/F064–065 và regression đã có; chỉ còn 4323-C.04 L (cả hai auth mode Gmail thật, delivery/read-back/attachment, gồm shared 4323-M.05), không coi local là live. [Ma trận](../acceptance-obligation-matrix.md#am-4323-c). **Lịch sử G6 trở về trước:** Hai dạng multipart, audit SEND_MAIL chỉ metadata gồm sender đã che, recipientCount/attachmentCount và toCount/ccCount/bccCount theo code mới; không lưu raw To/subject/body hoặc hash. Cần chấp thuận khác biệt literal privacy và Gmail thật. Retest local (không đóng literal): F013, F064, F065, F069, F070. Vòng C: [BE](../backend-supplemental-observations.md): C003–004/C010–017: send 200, một thư, durable CONFIRMED qua restart, đúng một SEND_MAIL. C019–024 lỗi drain không duplicate, C025–032 intent reject 503 không gửi, C033–038 outcome lỗi rồi phục hồi. Partial SMTP có validSent trả kết quả chưa rõ thay 400 giả, không tự resend. C040 privacy aggregate. Không ghi raw To/subject; contract OPEN riêng. **G4:** G4 R06–R08/R10 hoàn tất raw Transport create/connect/close và abort phía caller trước send. Không SMTP DATA fault mới, không retry; còn Gmail thật và quyết định contract metadata. [AM-4323-C ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4323-c). **G6:** 4323-C.03 D chưa duyệt; 4323-C.04 L giữ shared 4323-M.05. Manual local PASS không đóng contract.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_SEND_MAIL, MAX_ATTACHMENT_SIZE_MB. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng. Vòng C: [BE](../backend-supplemental-observations.md): C003–004/C010–017: send 200, một thư, durable CONFIRMED qua restart, đúng một SEND_MAIL. C019–024 lỗi drain không duplicate, C025–032 intent reject 503 không gửi, C033–038 outcome lỗi rồi phục hồi. Partial SMTP có validSent trả kết quả chưa rõ thay 400 giả, không tự resend. C040 privacy aggregate. Không ghi raw To/subject; contract OPEN riêng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (local agent, không human/live). **G6:** PASS manual local multipart đúng hành vi đang triển khai: H027/H040/H042/H120/H136–140, F013/F014/F064–065/F069–070, G045–063/G079–080, J001–005/J009–011, T009–013, F205/F213–217, C/D/E, G4 R06–R08/R10. Không tính setup H036 hoặc hash lỗi trước fix. Có direct HTTP từng request (không đổi nhãn thành Postman), delivery/read-back/bytes local và no retry khi uncertain. Giữ 4323-M.05 S/L trong 4323-C.04: Gmail thật cả hai auth mode, delivery/read-back/attachment, TV5. Contract 4323-C.03 D về audit metadata-only vẫn OPEN, không phê duyệt raw To/subject. Không observation mới; **Lịch sử evidence trước G6 (không phải trạng thái hiện tại):**. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4323 (H refs trong hàng AM-4323, PARTIAL): H026 search trước gửi 0; H027 multipart cũ trả 200 SENT, H028 nhận UID 61 có attachment; H030 tải 17 byte; H036 setup sai 200 không tính, H040 xác nhận flag tắt, H042/H120 403 FEATURE_DISABLED; H093 hai audit SEND_MAIL đã che sender, tổng count. H136 địa chỉ sai 400 INVALID_EMAIL; H137–139 cap 1 MiB tạm, multipart mới vượt cap 413 rồi restore; H140 modern 200 SENT, H141 nhận UID 63. Retest bản cuối: [F013](../backend-final-observations.md) (OBSERVED): POST multipart qua HTTPS trực tiếp 200 SENT, messageId/sentAt có mặt; không dùng status label một mình, F014 audit và F064–065 kiểm phía nhận.; [F064](../backend-final-observations.md) (OBSERVED): User ui_other GET list search marker 200 total1 UID13, delivery phía CC độc lập.; [F065](../backend-final-observations.md) (OBSERVED): User ui_other tải attachment UID13 index0 200 application/octet-stream, test.bin, Content-Length=22, decoded 22 bytes, SHA-256 8a5bdb4cc15164126c6ef2668de9dd240d299ce6397a42c95a9411b93d080ed8. F013/014 sent + group counts 1/1/1; H027/H140 legacy/modern sent, H042/H120 disabled403, H136 invalid recipient400, H138 oversized413.; [F069](../backend-final-observations.md) (OBSERVED): POST multipart trực tiếp bị 400 VALIDATION_ERROR, thông báo tiêu đề tối đa 200 ký tự.; [F070](../backend-final-observations.md) (OBSERVED): POST multipart trực tiếp bị 400 VALIDATION_ERROR, thông báo nội dung tối đa 1 MB. Kết hợp F013/014/064–065 send/audit To/CC/BCC counts và delivery CC/hash, H027/H140 legacy/modern sent, H042/H120 disabled403, H136 invalid recipient400, H138 oversized413.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G045–063, G079–080, G092; J001–005/J009–011: BCC nhận độc lập, nhiều MIME đúng hash sau fix Base64, UTF-8 đúng, header injection/51 recipients 400, SMTP outage 502 rồi phục hồi đúng một marker sau kiểm vắng mặt; audit chỉ success và metadata. Bổ sung vòng 61: [backend-supplemental-observations.md](../backend-supplemental-observations.md) T009–013: SMTP timeout 504 JSON trước greeting/auth, hai socket tự đóng; sau restore search marker total0, không retry mutation. Phần còn lại tại mốc trước F: Còn SMTP auth failure và Gmail live/human Postman multipart nếu yêu cầu đúng công cụ. Audit storage fault đã kiểm; contract raw To/subject OPEN riêng. Bổ sung A/B và regression cuối: [backend-supplemental-observations.md](../backend-supplemental-observations.md) — A010–011 tái hiện audit fail gây 502 dù đã giao đúng 1 thư. B010–011 sửa thành 200 SENT và đúng 1 thư; B020/B029 log counters, B026–028 phục hồi ghi audit. SMTP auth failure và Gmail thật/contract raw To/subject vẫn thiếu. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [BE](../backend-supplemental-observations.md): C003–004/C010–017: send 200, một thư, durable CONFIRMED qua restart, đúng một SEND_MAIL. C019–024 lỗi drain không duplicate, C025–032 intent reject 503 không gửi, C033–038 outcome lỗi rồi phục hồi. Partial SMTP có validSent trả kết quả chưa rõ thay 400 giả, không tự resend. C040 privacy aggregate. Không ghi raw To/subject; contract OPEN riêng. Vòng D: D001–003: status aggregate 401/403/200 ADMIN no-store. D005–016: capacity fixture 2, send/flag xác nhận, PREPARED2/localDeferred2; gửi thứ ba 503 AUDIT_UNAVAILABLE, read-back total0; restore drain đúng SEND_MAIL1/MAIL_FLAG_CHANGE1, queue/local0. D017–027: send mới 200, đúng một thư; restart thật khi outcome chưa durable, PREPARED1 vẫn tồn tại, localDeferred0, không thêm audit success, cùng UID/read-back đúng một thư. D045: 8 nhóm canary 0; D046 flags giữ đúng. H2 test chống starvation, giới hạn capacity, trạng thái stale, guard CREATE Trash và mất reply. Không khôi phục outcome RAM đã mất; không auto resolve/replay mail. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): E023–041: reject INSERT audit, gửi 200; CONFIRMED giữ attempts/nextAttemptAt qua restart. Reject UPDATE outcome, gửi 200; PREPARED và receipt bền vững, localDeferred1 vẫn khôi phục sau restart thật. Restore outcome: CONFIRMED2/localDeferred0; restore audit: queue0, đúng 2 SEND_MAIL cùng hai eventId và actorId, mỗi thư read-back đúng 1, không gửi lại. V8 không suy PREPARED thành success, không atomic MySQL–Gmail; nếu crash trước receipt durable hoặc cả hai kho mất dữ liệu thì vẫn phải đối chiếu. E042 aggregate 8 nhóm đều 0; canary subject/body/filename và lỗi SQL được dùng trong E, không có chứng cứ mới cho mọi nhóm credential/path/recipient hoặc Gmail live. Raw To/subject contract và SMTP auth/live vẫn OPEN. Phần còn lại tại mốc trước F: Còn SMTP auth failure 422, provider failure giữa thao tác thực tế và Gmail thật nếu phạm vi bắt buộc; E receipt/backoff và partial-send regression đã có. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): F205 422 GMAIL_AUTH_FAILED; F206 deliveries=0, F211 q đúng total0. F213–217 lỗi provider sau delivery thật trong GreenMail trả 502 GMAIL_CONNECTION_ERROR và thông báo chưa xác định; marker đúng 1 UID61, audit uncertain1/success0, không retry, socket0. F226 marker privacy0. Không chứng minh raw Transport, Gmail partial delivery/live hoặc human Postman multipart; contract raw To/subject riêng còn OPEN. MailAuditRecoveryIntegrationTest 13/13 (3 test mới): journal fail closed khi write/remove lỗi, kiểm lock/root và cả hai sibling receipt/pending, inventory không đầy đủ không được tin; giữ conflict/corrupt, không tự xóa. Recovered receipt phải validate lại trước finalize; unreserved finish không biến PREPARED thành success, observation đầu tiên không bị ghi đè; replay chỉ audit, không SMTP/IMAP mutation. Runtime fault test H2 giữ PREPARED khi DB reject, ghi đúng một audit từ observation còn RAM khi DB hồi phục, không duplicate và chỉ mở admission khi receipt xử lý xong. Không suy thành bảo đảm mọi OS crash/disk fault hay atomic MySQL–Gmail. **Còn thiếu hiện tại:** Gmail thật/raw Transport lifecycle và phạm vi Postman multipart/human nếu yêu cầu; local SMTP auth422, post-delivery failure502, read-back đúng một và uncertain audit đã đủ F205/F213–217. Không suy sang mọi Gmail/partial/network-mid-mutation; raw To/subject contract riêng. Không thay trạng thái checkbox. **Vòng G2:** SMTP Authenticator của wrapper dùng cùng phạm vi host/protocol/port/user như raw adapter; GreenMail 10/10, MailServiceFailureRegressionTest 22/22. Không đổi journal, không retry external mutation, không ghi To/subject. Raw Transport manual/live vẫn thiếu. **Còn thiếu:** Raw Transport caller-owned local regression có, còn manual được phép/Gmail thật. SMTP auth422, post-delivery unknown và đúng một thư/audit F giữ nguyên. Contract audit metadata riêng. Không đổi checkbox. **Vòng G3:** G3 guard raw/wrapper Transport trước cấp kết nối và sau open, không thay SMTP journal hoặc retry mutation. Giữ F post-delivery unknown, raw manual/Gmail live và contract privacy riêng. Không đổi checkbox. **G4:** G4 R06–R08/R10 hoàn tất raw Transport create/connect/close và abort phía caller trước send. Không SMTP DATA fault mới, không retry; còn Gmail thật và quyết định contract metadata. [AM-4323-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4323-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M10] Gửi mail — SMTP**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
