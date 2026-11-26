# [BE] MIME fixtures + Mail DTO

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M08] Nhận mail — IMAP Fetch |
| Owner | TV4 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 26/10/2026 → 29/10/2026 |
| Ước lượng | 2h |
| Priority | 1 |

---

## 2. Mục tiêu

Hoàn thành `[BE] MIME fixtures + Mail DTO` thuộc **[M08] Nhận mail — IMAP Fetch** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- Định nghĩa `MailSummaryDto {uid, from, subject, date, isRead, hasAttachment}` + `MailDetailDto {uid, from, to[], cc[], bcc[], subject, date, bodyText, bodyHtml, attachments[]}`
- `MailAttachmentDto {index, fileName, contentType, sizeBytes}`
- Chuẩn bị 5+ email test: text-only, HTML-only, multipart/mixed với PDF, đính kèm nhiều file, mail có ký tự UTF-8
- Gửi các mail này vào Gmail test account để dùng cho manual test

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu gốc ở trên được giữ nguyên. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Contract hiện tại:** `Mail Server/src/main/java/com/mailapp/dto/mail/MailDtos.java` định nghĩa MailSummaryDto, MailDetailResponse, MailAttachmentDto; flags.seen và alias isRead, textBody/htmlBody và alias bodyText/bodyHtml, filename/size và alias fileName/sizeBytes, bổ sung uidValidity, inline/contentId, attachmentCount, truncated. `mail/service/MailServiceImpl.java#walkMime` dùng chung thứ tự attachment cho list/detail/download; guard ownership/bảo trì ở nghiệp vụ, không audit riêng fixture/DTO.
- **Fixture trong repo (tạo bằng mã, không chứa thư thật):** `MailMessageBuilderTest#plainTextAndUtf8HeadersSurviveMimeRoundTrip` (text-only + UTF-8), `#htmlOnlyCreatesPlainAlternativeWithoutUnsafeScript` (đầu vào HTML-only, MIME có fallback text), `#pdfAttachmentAndTextBodyRoundTrip` (PDF bytes fixture), `#nestedAlternativeAndMultiplePngTextAndEmptyAttachmentsRoundTrip` (PNG/text/nhiều file), `MailServiceGreenMailIntegrationTest#testReadComplexMaliciousMailAndDownloadAttachment` (UTF-8, inline cid, XSS), `#attachmentFilteringAndDownloadUseIdenticalRecursiveMimeOrder` (thư chuyển tiếp có hai PDF trùng tên khác bytes/text, thứ tự/index/byte). Dữ liệu PDF là fixture byte phục vụ MIME, không phải chứng nhận PDF hiển thị hợp lệ. `MailControllerCompatibilityTest` kiểm hai shape list/detail và metadata alias.
- **Chưa xác minh:** Gmail live vẫn SKIP; không có bằng chứng đã gửi đủ 5+ thư mẫu vào tài khoản Gmail test thật. Cần tạo hộp thư test và gửi các mẫu tương ứng để manual Postman/Swing, không dùng credential hoặc email riêng tư.

- **Đối chiếu bổ sung:** Cập nhật `MailServiceGreenMailIntegrationTest#fiveIndependentIncomingMimeFixturesCoverOriginalAcceptanceCases`: 5 mail độc lập text-only, HTML-only thật, PDF hợp lệ 1 trang, nhiều PNG/text và UTF-8 có dấu; deliver vào GreenMail, kiểm list/detail metadata và download bytes theo index. Suite local đã có PASS; chưa gửi 5+ mẫu tới Gmail thật. Rà N/A: fixture/DTO riêng không có HTTP caller/feature/audit mutation; các API đọc/tải sử dụng DTO vẫn kiểm quyền, bảo trì/size và không ghi nội dung audit.

- **Bổ sung vòng G2 (sau F):** Local 7 MIME fixtures/evidence trước giữ nguyên, HTML-only quote Client đã sửa; live fixture test opt-in trong repo chưa chạy.

- **Bổ sung G4:** G4 live MIME harness source xuất ordinal/UID/UIDVALIDITY/attachment count sau verify bytes, giữ thư cho manual; compile-only, chưa gửi hoặc xóa Gmail thật.

- **Bổ sung G6:** Giữ shared 4320-M.05: EP-M1 special-use/count đối chiếu Gmail web trong demo TV4, ngoài 7 mẫu MIME thật, đọc/tải/hash/cleanup đúng marker và privacy. Không thay bằng local MIME.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 0 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 3 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailControllerCompatibilityTest: 12/12 PASS; MailMessageBuilderTest: 5/5 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; RealGmailMimeFixturesTest: 0/1 PASS, 1 SKIP (1 SKIPPED); AttachmentSizeAccuracyTest: 3/3 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec — OPEN. RealGmailMimeFixturesTest opt-in KEEP-FIXTURES chuẩn bị 7 mẫu MIME riêng (plain, HTML, alternative, PDF, PNG/text nhiều file, UTF-8, nested), kiểm byte và giữ mẫu cho manual; RealGmailIntegrationTest 2/2 SKIP; RealGmailXOAuth2IntegrationTest 2/2 SKIP; RealGmailMimeFixturesTest 1/1 SKIP. Chưa gửi đủ mẫu vào Gmail test thật. Retest local (không đóng literal): F068. **G4:** G4 live MIME harness source xuất ordinal/UID/UIDVALIDITY/attachment count sau verify bytes, giữ thư cho manual; compile-only, chưa gửi hoặc xóa Gmail thật. [AM-4397-C ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4397-c).
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). DTO và MIME fixture không thực thi owner/flag/audit; parse/download endpoints phải kiểm quyền.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). DTO và MIME fixture không thực thi owner/flag/audit; parse/download endpoints phải kiểm quyền.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). DTO và MIME fixture không thực thi owner/flag/audit; parse/download endpoints phải kiểm quyền.
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log — OPEN. Dedicated live opt-in, masking/safe-stage, không đọc thư tùy ý; chỉ thư synthetic marker/UID, không in secret, không xuất raw To/subject/body. RealGmailIntegrationTest 2/2 SKIP; RealGmailXOAuth2IntegrationTest 2/2 SKIP; RealGmailMimeFixturesTest 1/1 SKIP. Chưa quan sát response/log và quyền trên Gmail test thật; fixture/code không thay thế kiểm chứng live. **G4:** G4 live MIME harness source xuất ordinal/UID/UIDVALIDITY/attachment count sau verify bytes, giữ thư cho manual; compile-only, chưa gửi hoặc xóa Gmail thật. [AM-4397-P ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4397-p).
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4397 (H refs trong hàng AM-4397, PARTIAL): H019/H020 UTF-8 và 3 attachment (hai tên trùng + PNG), H100 HTML với ảnh từ xa bị chặn, H021/H022 tải hai file, H030 binary. Retest bản cuối: [F068](../backend-final-observations.md) (PARTIAL): F010 metadata ESTIMATED 3 parts, F011/012 duplicate file exact bytes/hash, F063 1048577 byte và F068 zero-byte exact; H019–022/H100 có plain/HTML đã sanitize.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) J001–005/J009–011: local HTML/plain UTF-8 và PDF/PNG/TXT qua SMTP/IMAP đúng hash, metadata ESTIMATED tách decoded size; regression thêm zero-byte. Phần còn lại hiện tại: Bộ 7 MIME trên Gmail dedicated thật, privacy live và cleanup theo UID chưa thực hiện. Builder chung đã rehearsal đầy đủ trên GreenMail, không dùng local fixture đóng live. Bổ sung A/B và regression cuối: [backend-supplemental-observations.md](../backend-supplemental-observations.md) — DefaultMailConnectionFactoryGreenMailTest.allSevenDedicatedMimeSamplesRehearseThroughLocalSmtpAndImap chạy chính builder 7 mẫu dành cho live qua GreenMail thật; exact attachment bytes/metadata. Live suite RealGmailMimeFixturesTest vẫn SKIP; safeStage bổ sung canary log; cleanup UID thật chưa chạy. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. **Vòng G2:** Local 7 MIME fixtures/evidence trước giữ nguyên, HTML-only quote Client đã sửa; live fixture test opt-in trong repo chưa chạy. Không đổi checkbox. **G4:** G4 live MIME harness source xuất ordinal/UID/UIDVALIDITY/attachment count sau verify bytes, giữ thư cho manual; compile-only, chưa gửi hoặc xóa Gmail thật. [AM-4397-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4397-m). **G6:** Giữ shared 4320-M.05: EP-M1 special-use/count đối chiếu Gmail web trong demo TV4, ngoài 7 mẫu MIME thật, đọc/tải/hash/cleanup đúng marker và privacy. Không thay bằng local MIME.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M08] Nhận mail — IMAP Fetch**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
