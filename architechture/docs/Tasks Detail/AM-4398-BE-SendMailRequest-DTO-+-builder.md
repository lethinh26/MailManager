# [BE] SendMailRequest DTO + builder

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M10] Gửi mail — SMTP |
| Owner | TV5 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 27/10/2026 → 30/10/2026 |
| Ước lượng | 2h |
| Priority | 1 |

---

## 2. Mục tiêu

Hoàn thành `[BE] SendMailRequest DTO + builder` thuộc **[M10] Gửi mail — SMTP** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- Contract `SendMailRequest {gmailAccountId, to, cc, bcc, subject, body, isHtml}` (DTO phần fields, files truyền riêng `MultipartFile[]`)
- Test `MimeMessage` basic bằng `javax.mail` đứng một mình: build message với body text + 1 attachment → verify `msg.getContent()` parse đúng
- Chuẩn bị test data: file PDF nhỏ + ảnh PNG + file text để test đa dạng MIME type

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu gốc ở trên được giữ nguyên. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Contract hiện tại:** `Mail Server/src/main/java/com/mailapp/dto/mail/MailDtos.java#SendMailRequest` gồm gmailId (alias gmailAccountId), to/cc/bcc, subject, textBody/htmlBody, inReplyTo/references; `controller/MailController.java#sendMail` nhận JSON part payload và attachments riêng, hoặc form gốc body/isHtml/files (files[] cũng được), map vào hai body rõ ràng và dùng chung validation. Giữ tương thích Swing và client cũ; response có messageId/status và message/sentAt. `mail/service/MailMessageBuilder.java#build` dùng Jakarta Mail UTF-8, text hoặc alternative và mixed nếu có file; encode filename, DataSource stream, hỗ trợ file rỗng có tên. `MailServiceImpl.java#sendMail` kiểm tra ownership, bảo trì, ALLOW_SEND_MAIL, giới hạn/recipient; audit SEND_MAIL ghi gmailId, recipientCount, attachmentCount, không lộ tiêu đề/nội dung/địa chỉ nhận.
- **Test có trong repo:** `Mail Server/src/test/java/com/mailapp/mail/service/MailMessageBuilderTest.java` round-trip độc lập qua MimeMessage.writeTo rồi parse MimeMessage: plain text/UTF-8 headers, HTML-only input, PDF bytes fixture + text body, PNG/text/file rỗng nhiều file, filename/bytes. `MailServiceGreenMailIntegrationTest` kiểm SMTP service, giới hạn và audit. `MailControllerCompatibilityTest` kiểm hai dạng multipart và validation HTTP.
- **Chưa xác minh:** suite lần này, multipart HTTP thật qua Postman và gửi nhận Gmail test (AM-4323/4361). Fixture MIME kiểm bytes/content type, không chứng minh nội dung PDF/PNG render hợp lệ.

- **Đối chiếu bổ sung:** Rà soát N/A: builder DTO độc lập không có ownership/audit riêng, nhưng AM-4323 caller phải kiểm ownership, maintenance, ALLOW_SEND_MAIL và audit. Dữ liệu 5 fixture nhận mới ở AM-4397 bổ sung PDF hợp lệ/PNG thật; per-file SMTP được kiểm theo AM-4323. Giữ Client multipart cũ/mới, Jakarta Mail thay javax.mail.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailControllerCompatibilityTest: 12/12 PASS; MailMessageBuilderTest: 5/5 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). DTO/builder MIME không trực tiếp kiểm owner/audit; gửi thật theo AM-4323.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_SEND_MAIL và giới hạn attachment ở service. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). DTO/builder MIME không trực tiếp kiểm owner/audit; gửi thật theo AM-4323.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (phạm vi local đã kiểm chứng). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4398 (H refs trong hàng AM-4398, PARTIAL): H027 legacy multipart 200, H028 received có attachment, H030 Postman download 17 byte/hash, H093 audit count; H140 modern multipart 200, H141 nhận thư. Retest bản cuối: [F070](../backend-final-observations.md) (PARTIAL): F013 multipart direct SENT, F064 CC recipient marker, F065 attachment 22 byte/hash; F058/063 lớn và F066–068 zero-byte; F069/070 subject/body validation, H027 legacy/H140 modern send200.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G045–053 phát hiện file PDF/TXT biến byte qua SMTP. Sửa MailMessageBuilder Base64; J001–005/J009–011 thực gửi/nhận, HTML/plain UTF-8, tải PDF/PNG/TXT bằng Postman/Newman từng request, Content-Length/decoded bytes/SHA-256 đều khớp. Regression MIME standalone và SMTP/IMAP có thêm zero-byte. Phần còn lại hiện tại: Đủ local DTO/builder/MIME của §4; multipart gửi qua HTTPS trực tiếp (không gọi là Postman). Gmail SMTP thật vẫn riêng ở AM-4323/4361; human sign-off riêng. Tách riêng human sign-off, live, contract và kết quả tự động.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M10] Gửi mail — SMTP**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
