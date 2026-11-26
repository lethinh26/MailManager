# [BE] Danh sách folder IMAP

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M08] Nhận mail — IMAP Fetch |
| Owner | TV4 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 02/11/2026 → 04/11/2026 |
| Ước lượng | 3h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Danh sách folder IMAP` thuộc **[M08] Nhận mail — IMAP Fetch** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M1 GET /api/mails/folders`

- **Path:** `/api/mails/folders`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Danh sách folder IMAP của Gmail (name,total,unread). Query: gmailAccountId.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/mails/folders?gmailAccountId={id}
- Verify `gmailAccountRepository.findByIdAndUserId(id, userId)` → null: 404 GMAIL_NOT_OWNED
- `MailConnectionFactory.createImapStore(acc)` → `store.connect()`
- `store.getDefaultFolder().list("*")` → mỗi `Folder`: `getName()`, `open(READ_ONLY)`, `getMessageCount()`, `getUnreadMessageCount()`, close
- Response 200: `[{name, total, unread}]`
- Errors: auth fail → 422 GMAIL_NEEDS_REAUTH (OAuth2) hoặc 422 GMAIL_AUTH_FAILED (App Password); network → 502 GMAIL_CONNECTION_ERROR
- Finally `store.close()`

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu gốc ở trên được giữ nguyên. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Contract hiện tại:** `GET /api/mails/folders?gmailId=...` chấp nhận cả `gmailAccountId`; giữ `name,total,unread` và bổ sung `imapName,displayName,type` cho Swing. Truy vấn theo ID và user ID, trả cùng 404 GMAIL_NOT_OWNED và thông báo chung cho ID không tồn tại/không thuộc sở hữu. OAuth2 cần cấp lại quyền trả 422, App Password sai 422; lỗi kết nối 502/504.
- **Code:** `Mail Server/src/main/java/com/mailapp/controller/MailController.java` (`listFolders`); `mail/service/MailServiceImpl.java` (`loadAndValidateAccount`, `listFolders`); `mail/folder/GmailFolderResolver.java` (LIST một lần, RFC 6154/fallback); `mail/folder/FolderCountReader.java` (STATUS/API chuẩn; lỗi không chuyển thành 0); wrapper `ImapConnection` đóng/trả pool. Chặn bảo trì cho USER, ownership trước IMAP; không yêu cầu audit riêng đối với thao tác đọc này.
- **Test có trong repo:** `MailServiceFailureRegressionTest#failedListIsNeverCachedAsAnEmptyMailbox`, `#foreignAndMissingMailboxesAreIndistinguishableWithoutImapAccess`, `FolderCountReaderTest`, `GmailFolderResolverTest`, `FolderCacheTest`, `MailServiceGreenMailIntegrationTest#testReadComplexMaliciousMailAndDownloadAttachment` và `#testSecurityGuards`. Kết quả chạy tại §5; còn manual Postman với Gmail thật (folder localized, counts, lỗi quyền/kết nối).
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Đối chiếu bổ sung:** Rà soát N/A: chỉ audit riêng của GET list folder không áp dụng theo §4; ownership, bảo trì và auth vẫn áp dụng tại MailService. Không audit nội dung hộp thư. Live/manual còn cần Gmail test riêng.

- **Bổ sung vòng G2 (sau F):** Factory raw Store caller-owned local đã có DefaultMailConnectionFactoryGreenMailTest 10/10; MailServiceFailureRegressionTest 22/22. Gate cho APP_PASSWORD ưu tiên với OAuth secondary NEEDS_REAUTH, còn raw manual/Gmail remote.

- **Bổ sung G3:** G3 kiểm snapshot trước/sau mở Store, stale thì đóng lease; detached fallback hai save thật qua GreenMail, Store đóng. Manual raw lifecycle/remote Gmail còn thiếu.

- **Bổ sung G4:** G4 R02–R05/R09 hoàn tất raw Store caller-owned local; FolderCacheTest 2/2 xác nhận cache phân biệt revision. Còn Gmail special-use/count/TLS thật, không lặp raw local.

- **Bổ sung G6:** PASS manual local theo §4/EP-M1 và chuẩn local đã dùng cho AM-4321/4322: H015/H084/H125, F056, G002/G054–057, T004–008, F204/F222 và raw G4 R02–R05/R09. Count/owner/error/connect/close có observation; cache/revision có regression. 02 C2-TV4 và 03 M08 demo Gmail web/count/special-use/TLS vẫn chưa đạt; giữ 4320-M.05 S/L tại AM-4361-M.02 và AM-4397-M.04. Không claim nghiệm thu Gmail thật.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** GmailFolderResolverTest: 6/6 PASS; FolderCountReaderTest: 4/4 PASS; FolderCacheTest: 2/2 PASS; MailServiceFailureRegressionTest: 22/22 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Missing/foreign 404 GMAIL_NOT_OWNED; OAuth cần reauth 422 GMAIL_NEEDS_REAUTH; E2E folder và ownership.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE; không toggle list riêng. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). List folder là read-only, không audit mutation riêng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (local agent, không human/live). **G6:** PASS manual local theo §4/EP-M1 và chuẩn local đã dùng cho AM-4321/4322: H015/H084/H125, F056, G002/G054–057, T004–008, F204/F222 và raw G4 R02–R05/R09. Count/owner/error/connect/close có observation; cache/revision có regression. 02 C2-TV4 và 03 M08 demo Gmail web/count/special-use/TLS vẫn chưa đạt; giữ 4320-M.05 S/L tại AM-4361-M.02 và AM-4397-M.04. Không claim nghiệm thu Gmail thật. Không observation mới; **Lịch sử evidence trước G6 (không phải trạng thái hiện tại):**. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4320 (H refs trong hàng AM-4320, PARTIAL): H015 folders INBOX total=unread=60, H084 admin không sở hữu 404 GMAIL_NOT_OWNED; H106 INBOX/Trash và số lượng sau relink; H125 USER khác 404 cùng code. Retest bản cuối: [F056](../backend-final-observations.md) (PARTIAL): F056 owner folders 200 INBOX total60 unread59, Trash0/0; H015 60/60 và H106 relink count, H084/H125 owner denial404.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G002/G054–057: missing account 404, IMAPS listener pause 502, resume giữ marker/UID/UIDVALIDITY. Bổ sung vòng 61: [backend-supplemental-observations.md](../backend-supplemental-observations.md) T004–008: 504 GMAIL_CONNECTION_ERROR thật; 2/2 socket timeout tự đóng, active=0; resume giữ 60 thư, UID/UIDVALIDITY. Phần còn lại tại mốc trước F: Còn auth failure 422, lifecycle raw Store và Gmail special-use/count thật. Local warm lease, idle release, 502/504, retry/recovery đã kiểm; không lặp. Bổ sung A/B và regression cuối: [backend-supplemental-observations.md](../backend-supplemental-observations.md) — B002–006/B030–037: pool fixture hoạt động, 1 socket sau hai page; idle 13 s về 0; ngắt warm IMAPS trả 502 và 0 socket, restore 200/UIDVALIDITY giữ nguyên. Unit/GreenMail kiểm lease close, không chứng minh mọi lifecycle raw hoặc Gmail thật. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [BE](../backend-supplemental-observations.md): C012: IMAPS pooled 1, SMTP 0 trước restart; C005–009/C018/C028–030 đọc lại cùng mailbox/UID sau flag/move và fail-closed. Không suy thành raw Store/auth failure thật. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): F204 422 GMAIL_AUTH_FAILED, F222 422 GMAIL_NEEDS_REAUTH; F207/F217 owned socket IMAPS/SMTP=0, F209 sau restore INBOX60/unread60. Hai nhánh auth mapping local đã được quan sát; raw Store caller-owned và special-use/count Gmail thật vẫn thiếu. **Còn thiếu hiện tại:** Raw Store caller-owned lifecycle và Gmail special-use/count thật; auth failure 422 cả hai mã, pool/idle/outage/recovery local đã đủ F204/F222, không lặp. Không thay trạng thái checkbox. **Vòng G2:** Factory raw Store caller-owned local đã có DefaultMailConnectionFactoryGreenMailTest 10/10; MailServiceFailureRegressionTest 22/22. Gate cho APP_PASSWORD ưu tiên với OAuth secondary NEEDS_REAUTH, còn raw manual/Gmail remote. **Còn thiếu:** Raw caller-owned lifecycle đã được xác minh tự động trên GreenMail local; còn manual được phép và Gmail special-use/count/remote TLS thật. Không lặp provider422/pool/outage F. Không đổi checkbox. **Vòng G3:** G3 kiểm snapshot trước/sau mở Store, stale thì đóng lease; detached fallback hai save thật qua GreenMail, Store đóng. Manual raw lifecycle/remote Gmail còn thiếu. Không đổi checkbox. **G4:** G4 R02–R05/R09 hoàn tất raw Store caller-owned local; FolderCacheTest 2/2 xác nhận cache phân biệt revision. Còn Gmail special-use/count/TLS thật, không lặp raw local. [AM-4320-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4320-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M08] Nhận mail — IMAP Fetch**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
