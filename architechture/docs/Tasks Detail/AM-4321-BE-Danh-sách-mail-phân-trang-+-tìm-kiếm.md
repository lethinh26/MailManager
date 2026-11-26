# [BE] Danh sách mail phân trang + tìm kiếm

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M08] Nhận mail — IMAP Fetch |
| Owner | TV4 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 03/11/2026 → 06/11/2026 |
| Ước lượng | 4.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Danh sách mail phân trang + tìm kiếm` thuộc **[M08] Nhận mail — IMAP Fetch** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M2 GET /api/mails`

- **Path:** `/api/mails`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Danh sách mail phân trang theo folder, FetchProfile header-only; q= tìm bằng IMAP search.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/mails?gmailAccountId=&folder=&page=&size=&q=
- Verify ownership; mở IMAP, `store.getFolder(folder)` (URL-decode nếu có `[Gmail]/...`) → 404 nếu null
- `folder.open(READ_ONLY)`, total = `getMessageCount()`
- Tính range: page*size+1 đến min((page+1)*size, total) — Gmail UID order theo `getMessages(start,end)`
- `FetchProfile`: `Envelope`, `Flags`, `ContentInfo` — KHÔNG fetch body
- Nếu `q` present: `folder.search(new OrTerm(SubjectTerm, FromTerm))` hoặc `BodyTerm` tuỳ spec, sau đó paginate trên kết quả
- Map mỗi Message → `{uid (dùng UIDFolder.getUID), from, subject, date, isRead=flags.contains(Flag.SEEN), hasAttachment=parse ContentInfo}`
- Response 200: `{content:[...], page, size, totalElements:total, totalPages:ceil(total/size)}`
- Sắp xếp: mới nhất trước (sort by date desc) — Gmail IMAP thường trả tăng dần nên reverse

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu gốc ở trên được giữ nguyên. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Contract hiện tại:** `GET /api/mails` hỗ trợ `gmailId`/`gmailAccountId`, page 0-based; bỏ size hoặc <= 0 dùng `MAIL_PAGE_SIZE` (fallback 20), tối đa 100. Response `items,page,size,total,hasMore,uidValidity` đồng thời trả alias `content,totalElements,totalPages`; `flags.seen` và `isRead` có cùng giá trị; có attachmentCount, snippet rỗng, bộ lọc unread/hasAttachment/from/since. Sắp xếp toàn tập theo date giảm dần (receivedDate, fallback sentDate, null cuối; UID giảm dần khi trùng ngày) trước phân trang.
- **Code:** `Mail Server/src/main/java/com/mailapp/controller/MailController.java#listMails`, `mail/service/MailServiceImpl.java#listMails`, `#fetchMatchingMessages`, `#fetchEnvelopeProfile`, `#walkMime`, `dto/mail/MailDtos.java`. Batch 500 ENVELOPE/UID toàn tập để #orderByDate; FLAGS/CONTENT_INFO chỉ fetch trang hoặc lô 100 khi lọc attachment; thử X-GM-RAW hoặc fallback Subject/From; FetchProfile ENVELOPE/FLAGS/CONTENT_INFO/UID, duyệt MIME container không chủ động đọc leaf body. Lọc đính kèm true/false trên mọi kết quả theo lô, không cắt 300; total tính sau lọc. Dùng guard ownership/bảo trì chung; không audit riêng thao tác đọc.
- **Test có trong repo:** `MailServiceFailureRegressionTest#attachmentSearchIncludesOldestMessageBeyondFirst300AndFalseFilter`, `MailServiceGreenMailIntegrationTest#testSendSearchPaginateFlagsAndDeleteLifecycle`, `#attachmentFilteringAndDownloadUseIdenticalRecursiveMimeOrder`, `MailPerformanceAndResilienceTest#testPerformanceBenchmarks_30_100_10MB_25MBStream`. Kết quả chạy tại §5; cần Postman/Gmail thật để kiểm tra X-GM-RAW, hiệu năng hộp thư lớn và tính đúng của BODYSTRUCTURE trên provider thực. `MailControllerCompatibilityTest#listsBothCurrentAndOriginalPaginationFields` kiểm cả hai shape qua MockMvc. `MailServiceFailureRegressionTest#dateDescendingPaginationUsesUidTieBreakAndSearchFallbackWithoutFetchingBodies` kiểm ngày lệch UID, tie, null, nhiều trang và fallback. Kết quả chạy tại §5. Offset có thể dịch khi hộp thư thay đổi giữa request.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Đối chiếu bổ sung:** Rà soát N/A: §4 chỉ đọc metadata/search nên không cần audit riêng. Ownership và bảo trì bắt buộc. Bổ sung `MailPerformanceAndResilienceTest#thousandMessageMailboxKeepsAdjacentDeepPagesOrderedWithoutDuplicates` với 1000 thư UTF-8 thật trên GreenMail + 2 thư file lớn, trang sâu qua ranh giới batch 500 và hai trang search; `MailServiceFailureRegressionTest#dateDescendingPaginationUsesUidTieBreakAndSearchFallbackWithoutFetchingBodies` kiểm thứ tự ngày, UID và không đọc leaf body. Chưa chạy lại các test mới; offset pagination chỉ ổn định trên mailbox không đổi giữa hai request (UIDVALIDITY không phải snapshot).

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailControllerCompatibilityTest: 12/12 PASS; MailPerformanceAndResilienceTest: 5/5 PASS; MailServiceFailureRegressionTest: 22/22 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). DTO alias và sắp xếp toàn bộ theo receivedDate (fallback sentDate), giảm dần, UID tie-break trước phân trang; test offline, chưa đo Gmail lớn.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE, MAIL_PAGE_SIZE; không toggle read riêng. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). List/search read-only, không audit mutation riêng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4321 (H refs trong hàng AM-4321, OBSERVED): H016–017 trang 0/1, size 10, tổng 60, UID 60–51 và 50–41, không lặp (cùng ngày fixture); H018 search UID 6; H099 filter attachment trả UID 8/6, count 1/3. Retest bản cuối: [F036](../backend-final-observations.md) (OBSERVED): List unread page0 size5: total61, 13 pages, hasMore=true; UID61 date 07:07:31 mới hơn, rồi UID60–57 cùng 07:05:43 giảm dần; cả 5 seen=false, UID61 hasAttachment=true.; [F037](../backend-final-observations.md) (OBSERVED): Page6 size10 total61/7 pages, đúng một UID1, hasMore=false.; [F038](../backend-final-observations.md) (OBSERVED): Page7 size10 total61/7 pages, items=[] và hasMore=false.; [F042](../backend-final-observations.md) (OBSERVED): Sau F041 mark seen, search q+unread 200 total0, items=[], totalPages0.; [F043](../backend-final-observations.md) (OBSERVED): Search plain marker + hasAttachment filter 200 total0, items=[]; không coi label thay thế actual empty result.; [F044](../backend-final-observations.md) (OBSERVED): Search q+since trả đúng UID61 mới, total1/1 page, date 07:07:31, attachment true. H016–018 hai trang UID60–51/50–41 và search UID6, H099 attachment filter.. Phần còn lại: Scope list/paging/date-desc/tie/filter local đã quan sát; chưa có list foreign/missing folder riêng trên JAR cuối, from/since invalid, IMAP ENVELOPE/no-body là nội bộ, Gmail large-mailbox benchmark/live. Human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M08] Nhận mail — IMAP Fetch**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
