# [BE] Chi tiết mail + parse MIME

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M08] Nhận mail — IMAP Fetch |
| Owner | TV4 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 04/11/2026 → 07/11/2026 |
| Ước lượng | 4.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Chi tiết mail + parse MIME` thuộc **[M08] Nhận mail — IMAP Fetch** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M3 GET /api/mails/{gmailAccountId}/{folder}/{uid}`

- **Path:** `/api/mails/{gmailAccountId}/{folder}/{uid}`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Chi tiết mail: body text/html + metadata đính kèm; mở READ_WRITE đặt cờ \Seen.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/mails/{gmailAccountId}/{folder}/{uid}
- Verify ownership, URL-decode folder, open READ_WRITE (để set \Seen)
- `UIDFolder.getMessageByUID(uid)` → null: 404
- Parse message: `getFrom()[0]`, `getAllRecipients()` split TO/CC/BCC, `getSubject()`, `getReceivedDate()`
- Parse content recursive (`Multipart`):
  - `text/plain` → `bodyText`
  - `text/html` → `bodyHtml`
  - `Part.ATTACHMENT` → metadata `{index, fileName=MimeUtility.decodeText, contentType, sizeBytes=getSize()}` — KHÔNG đọc content
- Sau khi parse → `message.setFlag(Flags.Flag.SEEN, true)` → folder đóng sẽ commit
- Response 200: `{uid, from, to[], cc[], bcc[], subject, date, bodyText, bodyHtml, attachments[]}`

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu gốc ở trên được giữ nguyên. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Contract hiện tại:** path giữ nguyên, response `MailDetailResponse` dùng `textBody,htmlBody,attachments[].filename,size,flags,uidValidity` và trả alias `bodyText/bodyHtml/fileName/sizeBytes` của contract gốc. Có `loadRemote`, `markSeen=false` (preview READ_ONLY và PEEK, mặc định đánh dấu Seen), `truncated`, `hasBlockedRemoteImages`, reply headers; UIDVALIDITY sai trả 409, UID không tồn tại trả 404. Body mỗi dạng giới hạn 512 × 1024 ký tự, HTML-only có text fallback; metadata size là ước lượng.
- **Code:** `Mail Server/src/main/java/com/mailapp/mail/service/MailServiceImpl.java#getMailDetail`, `#walkMime`, `#appendBody`, `#buildAttachmentMeta`, `HtmlSanitizerService.java`, `FilenameSanitizer.java`, `dto/mail/MailDtos.java`. Mặc định mở READ_WRITE, xác nhận Seen; markSeen=false mở READ_ONLY + PEEK, không sửa Seen, nếu không ghi được cờ thì báo lỗi; không dùng cache chi tiết cũ. Duyệt multipart/message/rfc822 cùng quy tắc tải file, gồm text attachment có tên/inline/binary; HTML sanitize và chặn ảnh remote mặc định. Guard ownership/bảo trì; không có audit riêng cho auto-Seen trong spec.
- **Test có trong repo:** `MailServiceGreenMailIntegrationTest#testReadComplexMaliciousMailAndDownloadAttachment`, `#attachmentFilteringAndDownloadUseIdenticalRecursiveMimeOrder`, `MailServiceFailureRegressionTest#readOnlyFolderCannotPretendToMarkMessageSeen`, `MailSecurityAndSanitizerTest`. Chưa xác nhận kết quả hiện tại; cần manual Gmail thật, kiểm tra Seen từ web Gmail và UI Swing (HTML/charset/ảnh inline). `MailControllerCompatibilityTest#detailExposesBothBodyAndAttachmentNames` kiểm cả hai tên trường. `MailServiceGreenMailIntegrationTest#previewDoesNotMarkUnreadMailSeenButDefaultDetailDoes` kiểm preview và UIDVALIDITY; `MailControllerCompatibilityTest#previewQueryDelegatesToReadOnlyDetailOverload` kiểm HTTP.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Đối chiếu bổ sung:** Rà soát N/A: §4 yêu cầu tự đánh dấu Seen khi đọc, không yêu cầu audit riêng cho implicit Seen; toggle cờ tường minh có audit tại AM-4394. Bổ sung fixture MIME nhận độc lập 5 thư thật trên GreenMail ở AM-4397; không coi dữ liệu thư của người gọi hợp lệ là rò rỉ, vẫn phải kiểm quyền và sanitize.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailControllerCompatibilityTest: 12/12 PASS; MailSecurityAndSanitizerTest: 3/3 PASS; MailServiceFailureRegressionTest: 22/22 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS; RealGmailMimeFixturesTest: 0/1 PASS, 1 SKIP (1 SKIPPED); AttachmentSizeAccuracyTest: 3/3 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE; không toggle detail riêng. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Đọc detail/mark seen khi mở không có action audit riêng; explicit toggle theo AM-4394.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4322 (H refs trong hàng AM-4322, PARTIAL): H019 preview seen=false, H020 mở seen=true; body UTF-8, alias body, 3 attachment gồm hai tên trùng; H100 HTML sanitized có blocked remote image, H101 UID không tồn tại 404 MAIL_NOT_FOUND. H025 đọc lại flags. Retest bản cuối: [F010](../backend-final-observations.md) (OBSERVED): Preview markSeen=false UID6 200, bodyText tiếng Việt, From/To/date đúng shape, 3 metadata index0/1/2; hai fixture.txt trùng tên; size/sizeBytes 21/24/69, đều ESTIMATED; alias fileName/filename. Seen=false.; [F039](../backend-final-observations.md) (OBSERVED): Detail UID6 gửi UIDVALIDITY stale 409 UID_VALIDITY_CHANGED, không trả detail sai UID.; [F040](../backend-final-observations.md) (OBSERVED): USER khác GET detail UID6 của owner 404 GMAIL_NOT_OWNED.; [F041](../backend-final-observations.md) (OBSERVED): GET detail mặc định UID6 200 seen=true, cùng body/metadata/UID như F010 preview; F042 lọc unread+q trả 0. H019–020 preview/open, H100 HTML sanitizer có blocked remote (không chứng minh MIME gốc HTML-only), H101 missing UID404.. Phần còn lại: Scope detail/mark read local đã quan sát, gồm metadata và alias sizeAccuracy. Còn HTML-only/nested/CC/BCC đủ mẫu live, UID folder chứa slash, kiểm không fetch attachment leaf từ nội bộ và Gmail thật; human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng C: [BE](../backend-supplemental-observations.md): Mã detail đọc body qua PEEK trước explicit Seen; tránh đổi Seen ngầm khi parse lỗi. Suite MailServiceFailureRegressionTest và MailServiceGreenMailIntegrationTest chạy lại; không thêm manual detail.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M08] Nhận mail — IMAP Fetch**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
