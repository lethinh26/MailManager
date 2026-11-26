# [BE] Stream file đính kèm

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M12] File đính kèm — Tải về & Mở file |
| Owner | TV5 |
| Platform | API |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 09/11/2026 → 13/11/2026 |
| Ước lượng | 10h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Stream file đính kèm` thuộc **[M12] File đính kèm — Tải về & Mở file** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M4 GET /api/mails/{gmailAccountId}/{folder}/{uid}/attachments/{index}/download`

- **Path:** `/api/mails/{gmailAccountId}/{folder}/{uid}/attachments/{index}/download`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Stream file đính kèm thứ index, giải mã Base64, kiểm tra MAX_ATTACHMENT_SIZE_MB.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/mails/{gmailAccountId}/{folder}/{uid}/attachments/{index}/download
- Verify ownership; open folder READ_ONLY, `getMessageByUID(uid)` → null: 404
- Parse multipart → tìm part thứ `index` có `Part.ATTACHMENT` → null: 404 "Không có đính kèm"
- `part.getSize() > MAX_ATTACHMENT_SIZE_MB*1024*1024` → 413 PAYLOAD_TOO_LARGE
- Decode filename `MimeUtility.decodeText(part.getFileName())`
- Stream `part.getInputStream()` (JavaMail đã decode Base64) → response `OutputStream`
- Headers: `Content-Type=part.getContentType()`, `Content-Disposition: attachment; filename="${filename}"`, `Content-Length=part.getSize()`
- Không buffer cả file vào memory nếu có thể — dùng stream

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu gốc ở trên được giữ nguyên. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Contract hiện tại:** path giữ nguyên, index zero-based thống nhất với chi tiết MIME; uidValidity tùy chọn (409 nếu lệch), index âm 400, không tìm thấy file/UID 404. Header Content-Disposition RFC 5987, filename đã sanitize, Content-Type an toàn + nosniff, Content-Length là byte đã decode thực tế (không dùng part.getSize() có thể gồm Base64).
- **Code:** `Mail Server/src/main/java/com/mailapp/mail/service/MailServiceImpl.java#prepareAttachmentDownload`, `#collectAttachmentParts`, `#walkMime`, `#copyEmbeddedAttachment`, `#deleteDownloadSpool`; `MailService.java#AttachmentDownloadHandle`, `MailController.java#downloadAttachment`, `FilenameSanitizer.java`. Ownership/bảo trì, download rate limit; READ_ONLY, stream 64 KiB vào file temp giới hạn MAX_ATTACHMENT_SIZE_MB, 413 trước HTTP headers nếu quá lớn, stream tiếp ra client và dọn khi đóng/exception. Với attachment trong message/rfc822, parse raw MIME qua file temp tối đa 128 MiB để không phụ thuộc BODY section lồng của IMAP; xác minh index/đường dẫn MIME/filename/type/content-ID và giữ index. Không buffer toàn file trong RAM/DB; crash đột ngột có thể để lại temp. Spec không yêu cầu audit download riêng.
- **Test có trong repo:** `MailServiceGreenMailIntegrationTest#testReadComplexMaliciousMailAndDownloadAttachment` (SHA-256), `#attachmentFilteringAndDownloadUseIdenticalRecursiveMimeOrder` (index/kích thước và 2 file trùng tên khác bytes), `#attachmentLimitIsCheckedAgainstDecodedBytesBeforeResponse`; `MailPerformanceAndResilienceTest#testPerformanceBenchmarks_30_100_10MB_25MBStream`. Chưa xác nhận suite lần này; cần manual Postman tải byte thực, Content-Length/413/filename, gián đoạn kết nối và Gmail thật.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Đối chiếu bổ sung:** Rà soát N/A: §4 download không yêu cầu audit riêng; ownership, bảo trì, giới hạn size bytes vẫn bắt buộc. Test 5 MIME fixture tại AM-4397 bổ sung download đúng index và decoded bytes PDF/PNG/text. Chưa chạy lại test mới; manual cần Gmail riêng và tải file thật.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailPerformanceAndResilienceTest: 5/5 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS; MailControllerCompatibilityTest: 12/12 PASS; MailServiceFailureRegressionTest: 22/22 PASS; RealGmailMimeFixturesTest: 0/1 PASS, 1 SKIP (1 SKIPPED); AttachmentSizeAccuracyTest: 3/3 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE, MAX_ATTACHMENT_SIZE_MB; không toggle download riêng. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Stream download read-only, không audit mutation riêng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (phạm vi local đã kiểm chứng). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4324 (H refs trong hàng AM-4324, PARTIAL): H021 index 0 tên fixture.txt, 20 byte, SHA-256 606d4086c65a116d6aff0b8a18d04c4fd01fbf153c2dca0fb70617a105e13948; H022 index 1 cùng tên, 22 byte, SHA-256 44dd125b9432e121be6340eb6ee4e5829d44514f44557fc4e3186d6224070f14. Content-Length đúng từng tải. H023 index 99 404; H030 Postman binary 17 byte SHA-256 444074d5328d52b4e0036d37b1b6ea0a9fe3b0c96872d1157fbf01b6fdb2ce8d, header attachment; H102 foreign 404. Retest bản cuối: [F011](../backend-final-observations.md) (OBSERVED): GET attachment index0 200 text/plain, Content-Disposition RFC5987 fixture.txt, Content-Length=20 và 20 decoded bytes, SHA-256 606d4086c65a116d6aff0b8a18d04c4fd01fbf153c2dca0fb70617a105e13948.; [F012](../backend-final-observations.md) (OBSERVED): Index1 cùng filename nhưng 22 decoded bytes, Content-Length=22, SHA-256 44dd125b9432e121be6340eb6ee4e5829d44514f44557fc4e3186d6224070f14; khác index0, bằng hash fixture H022. Metadata F010 24 ESTIMATED không tuyên bố exact.; [F058](../backend-final-observations.md) (OBSERVED): POST send fixture giới hạn (HTTPS trực tiếp) 200 SENT; chưa coi size nguồn đã xác minh từ response gửi.; [F059](../backend-final-observations.md) (OBSERVED): Search marker trả 200 total1 UID62.; [F060](../backend-final-observations.md) (OBSERVED): ADMIN PUT settings 200, MAX_ATTACHMENT_SIZE_MB=1.; [F061](../backend-final-observations.md) (OBSERVED): Download UID62 index0 với UIDVALIDITY 413 PAYLOAD_TOO_LARGE JSON, giới hạn 1 MB; không trả nhầm binary 200.; [F062](../backend-final-observations.md) (OBSERVED): ADMIN PUT settings 200, MAX_ATTACHMENT_SIZE_MB=25 phục hồi.; [F063](../backend-final-observations.md) (OBSERVED): Cùng UID62 index0 tải lại 200 application/octet-stream, attachment fixture-limit.bin, Content-Length=1048577 = decoded bytes, SHA-256 9ef018e955cacc77ab7f2bc97ef79ed1415f0a028b89d026f7bc3551479b1b5c.; [F066](../backend-final-observations.md) (OBSERVED): HTTPS direct POST gửi zero-byte attachment 200 SENT.; [F067](../backend-final-observations.md) (OBSERVED): Search marker 200 total1 UID63.; [F068](../backend-final-observations.md) (OBSERVED): GET UID63 index0 200 application/octet-stream, attachment empty-fixture.txt, Content-Length=0, decoded 0 byte, SHA-256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855. F011–012 duplicate filename đúng byte/index; F061/063 cap reject/restore; H023 invalid index404, H102 foreign404.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G050–052 phát hiện MIME byte sai; J009–011 xác nhận nguồn/tải 584/68/35 byte và SHA-256 khớp; J021–022 binary Accept nhận 429 JSON + Retry-After đúng path, owner khác 200. Phần còn lại hiện tại: Giữ PASS local; Gmail thật và thao tác UI sau login riêng. Tách riêng human sign-off, live, contract và kết quả tự động.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M12] File đính kèm — Tải về & Mở file**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
