# Quan sát bổ sung Backend — 26/11/2026

## Phạm vi và nguồn

- H001–H150 và F001–F095 giữ nguyên là lịch sử; không cộng chúng vào kết quả chạy mới. G001–G092 là phiên MySQL/HTTPS/GreenMail/OAuth loopback trước hai sửa lỗi mới. G gồm 80 phản hồi ứng dụng, 11 thao tác điều khiển fault fixture, một đọc bộ đếm provider. J001–J022 là 22 phản hồi ứng dụng độc lập trên JAR đã sửa (bao gồm ba lỗi setup đường dẫn). Không đồng nhất số quan sát với số test PASS.
- Chỉ fixture synthetic do `Mail Server/scripts/test-client-e2e.ps1` tạo, certificate được tin cậy theo từng phiên, không dùng Gmail/DB/profile thật. ID/UID chỉ có nghĩa trong chính phiên. HTTP GET/POST từng request qua Newman hoặc HTTPS trực tiếp (multipart); không phải Google live hay human sign-off. Không retry thao tác đăng nhập/đổi mật khẩu/nhập credential đã bị safety gate chặn.
- Evidence scrubbed theo thứ tự dòng: `Mail Server/build/acceptance/manual-http-observed-G.ndjson`, `manual-http-observed-J.ndjson`; schema metadata tương ứng `manual-mysql-metadata-G.tsv`, `manual-mysql-metadata-J.tsv`. Lưu riêng F tại `manual-http-observed-F001-F095.ndjson` và `manual-mysql-metadata-F001-F095.tsv` (cùng thư mục). Không mở private manifest/log hoặc xuất credential, state, token, mail thật.

## G — quan sát từng nhóm request

| Ref | Task liên quan | Quan sát thực và giới hạn |
|---|---|---|
| G001 | AM-4347 | HTTPS tin cậy, TLS 1.3 và TLS_AES_256_GCM_SHA384. Không chứng minh Swing login hoặc handshake Gmail. |
| G002 | AM-4320 | Account không tồn tại: 404 GMAIL_NOT_OWNED. |
| G003 | AM-4343 | Content-Type không hỗ trợ: 415 UNSUPPORTED_MEDIA_TYPE. |
| G004–006 | AM-4344 | Đọc audit bootstrap đã có sẵn: LOGIN (5), CHANGE_PASSWORD (1), LINK_GMAIL (2). Không thực hiện lại thao tác credential bị chặn. |
| G007–008, G013–014 | AM-4333, AM-4351 | Admin tắt registration và OAuth, bật maintenance; GET public config xác nhận. Khôi phục flags và đọc lại đúng snapshot. Quan sát Swing tách riêng tại báo cáo bổ sung FE. |
| G009–012, G015–016 | AM-4325, AM-4344 | Registration tắt nhưng admin create USER 201, mustChangePassword=true, one-time temporaryPassword hiện diện, no-store; trùng username 409 USERNAME_TAKEN, dữ liệu không hợp lệ 400 VALIDATION_ERROR, USER create 403 FORBIDDEN, audit ADMIN_CREATE_USER; detail không trả credential. Các nhánh local còn thiếu trước đó đã được quan sát. Không nhập/cung cấp mật khẩu để lặp branch blocked. |
| G017–022 | AM-4316 | Quota=1: CREATE 409 GMAIL_LIMIT_REACHED; UPSERT method hiện hữu 200; kiểm shape HTTPS/response_type=code/Gmail scope/offline/redirect fixture/PKCE S256/challenge/state/poll đều đúng. State và poll khác nhau; không in giá trị. Restore quota=5, state độc lập PENDING 202. |
| G023–032 | AM-4317 | State invalid: HTML báo lỗi; provider fault thiếu Gmail scope: HTML lỗi, poll FAILED MISSING_GMAIL_SCOPE; profile mismatch: HTML lỗi, poll FAILED ACCOUNT_MISMATCH. Danh sách link vẫn một App Password, không sinh OAuth link sai identity. HTTP 200 HTML lỗi không tính là liên kết thành công. |
| G033–040, G066 | AM-4317, AM-4344 | Provider normal: authorize/callback thành công, poll SUCCESS CREATED; replay HTML lỗi, poll consumed 410 LINK_SESSION_EXPIRED; audit OAUTH2_CALLBACK thành công một dòng, LINK_GMAIL_DENIED_SCOPE một dòng; DTO link không credential. Không khẳng định mọi lỗi đều tạo OAUTH2_CALLBACK. |
| G041–044 | AM-4318 | Provider reject revoke; unlink 204 theo best-effort contract, App Password link còn lại; restore provider normal. |
| G045–053 | AM-4323, AM-4324, AM-4398 | SMTP multipart gửi thật ba file, To và BCC đều nhận cùng marker/message; không lộ BCC. G046/047 dùng sai query keyword nên không chứng minh filter; G048 sai route 404 là lỗi setup, G049 sửa route thấy detail 3 file. G050–052 tải binary: **PDF 584→614 byte, TXT 35→34 byte, hash không khớp; PNG 68 byte khớp**. Nội dung tiếng Việt trong G bị sai do PowerShell pipe encoding, không kết luận app lỗi UTF-8 từ lần này. |
| G054–057 | AM-4320, AM-4358, AM-4359 | Pause riêng listener IMAPS, 502 GMAIL_CONNECTION_ERROR; resume, đọc lại marker total=1 với cùng UID/UIDVALIDITY. Không mất mailbox khi fault. Chưa trực tiếp đo timeout và lease lifecycle, chưa Gmail thật. |
| G058–063 | AM-4323, AM-4358 | SMTP pause trước connect: 502 GMAIL_CONNECTION_ERROR, resume rồi xác nhận marker vắng mặt. Gửi mới có chủ đích sau read-back: SENT, đúng một marker. Không retry mutation khi kết quả chưa rõ. |
| G064–065 | AM-4316, AM-4317 | State G021 tồn tại quá 5 phút thực: callback HTML lỗi, poll 410 LINK_SESSION_EXPIRED. Không rút ngắn TTL để giả bằng chứng. |
| G067–078 | AM-4324, AM-4343 | Với giới hạn fixture 12, owner tải 12 lượt thành công (gồm G050–052), sau đó G076/077 **401 từ /error thay vì 429**; owner khác 200. Lỗi negotiation trên binary Accept, cần sửa. |
| G079–080 | AM-4323 | Subject chứa newline synthetic: 400 HEADER_INJECTION_DETECTED; 51 người nhận synthetic: 400 TOO_MANY_RECIPIENTS. Không gửi thư ngoài fixture. |
| G081–091 | AM-4319, AM-4332, AM-4344 | Link OAuth target synthetic, poll SUCCESS; provider reject revoke trong cascade: delete user 204, detail 404 USER_NOT_FOUND, session cũ /me 401, mailbox owner khác ACTIVE nguyên vẹn, audit ADMIN_DELETE_USER một link. Provider aggregate G090: 4 exchange, 3 UserInfo, 3 profile, 0 revoke thành công, 2 rejected; cả hai revoke failure thực sự được gọi, state invalid/replay/expiry không phát sinh exchange thừa. |
| G092 | AM-4323, AM-4344 | Hai audit SEND_MAIL thành công tương ứng lần multi-file và lần gửi sau phục hồi, count metadata đúng; không raw recipient/subject/body; không audit success cho SMTP fail/validation reject. |

## Hai lỗi sửa và kiểm chứng J

1. `MailMessageBuilder.java`: luôn đặt `Content-Transfer-Encoding: base64` cho attachment, kể cả file rỗng. MIME/SMTP không còn tự chọn 7bit/8bit làm đổi newline hoặc mất byte. Regression `MailServiceGreenMailIntegrationTest#outgoingAttachmentsPreserveExactBytesAcrossSmtpAndImap` kiểm tra PDF, PNG, UTF-8 TXT (LF/CRLF) và zero-byte qua SMTP rồi IMAP stream, byte-for-byte.
2. `GlobalExceptionHandler.java`: explicit `application/json` trên error response để binary Accept không làm handler negotiation thất bại rồi chuyển `/error` thành 401. Không thay đổi endpoint security hoặc RBAC. `MailControllerCompatibilityTest` bổ sung kiểm binary Accept với 429 JSON + Retry-After và 413 JSON.

| Ref | Kết quả quan sát trên JAR mới |
|---|---|
| J001–005 | Gửi multipart 200, To/BCC mỗi bên đúng một marker, HTML và plain fallback giữ chữ tiếng Việt, BCC không xuất hiện trong detail. Dùng JSON Unicode escape để tránh sai encoding shell. |
| J006–008 | 404 do nội suy PowerShell sai path, là lỗi setup; không dùng làm evidence chức năng. |
| J009–011 | Ba tải file đúng route qua Postman/Newman với Accept binary; Content-Length, byte thực tế và SHA-256 đều bằng file nguồn. Content-Type lần lượt PDF, PNG, text/plain. |
| J012–020 | 9 download bổ sung thành công; tổng 12 download của owner gồm J009–011. |
| J021 | Lượt thứ 13 trả **429 application/json, TOO_MANY_ATTEMPTS, Retry-After=874**, path chính xác của download, không còn 401 /error. Dừng tại giới hạn. |
| J022 | Owner khác tải PNG 200, bytes/hash đúng; không bị rate limiter owner đầu ảnh hưởng. |

| File synthetic | Byte nguồn | Byte tải G (trước sửa) | Byte tải J (sau sửa) | SHA-256 nguồn và J |
|---|---:|---:|---:|---|
| fixture.pdf | 584 | 614 | 584 | `4350017e10649e7e280d12c8d8eadf4fac8e63554b8a47efe08015cbc64fd77e` |
| fixture.png | 68 | 68 | 68 | `c2e0ac3ea3f3a5abdeebc2718724ca6926f8738f96a770e159ca8c65cebe3a0c` |
| fixture.txt | 35 | 34 | 35 | `e2288a621548ea77089869a29f6d08919653995191d1d0c21800ea6aa92e2bec` |

Size metadata IMAP của J là ESTIMATED, không dùng thay cho số byte tải. Không suy kết quả download đúng thành Gmail live, Swing sau login hoặc render PDF bằng ứng dụng ngoài.

## L — nâng cấp legacy và port/cert thực tế

Chạy `test-client-e2e.ps1 -SkipBuild -DatabaseEngine MySQL -VerifyDatabaseOnly -VerifyLegacyUpgrade -VerifyConfiguredPort` trên JAR mới, hoàn thành exit 0 lúc 14:43 ngày 26/11/2026. `LegacyUpgradeProbe.java` chạy Flyway V1–V2, tạo dữ liệu giả rồi thực hiện đúng migration V3–V6; không sửa migration lịch sử. Native MySQL 9.5.0, datadir độc lập, utf8mb4/utf8mb4_unicode_ci. Cổng 8443 trống trước khi start và thuộc đúng process fixture; kiểm trust/hostname bằng PEM mới, không dùng -k để kết luận TLS PASS. Dữ liệu test được dọn; không mở DB đang hoạt động hoặc keystore triển khai thật.

- L01: 2 user giữ nguyên ID/BCrypt/role/flags; 3 alias liên kết thành 2 mailbox đúng owner; hai auth methods của mailbox hợp nhất và mailbox owner khác giữ nguyên ciphertext. Audit 1→2 (thêm merge audit), settings 7→9, giữ registration=false và quota legacy=0. Widen/unique/FK được kiểm, 4 insert không hợp lệ bị constraint từ chối.
- L02: schema conflict riêng có duplicate email ở V4; V5 fail trước DDL, giữ cả hai user và hash nguyên vẹn, không tự chọn/xóa identity.
- L03: AES-256-GCM với key synthetic: round-trip, khác ciphertext giữa hai lần mã hóa, 12-byte IV + 16-byte tag overhead, từ chối tag sửa và key khác; so sánh trong process, chỉ xuất PASS/count, không xuất ciphertext/key/plaintext. Dữ liệu ciphertext giả ở migration được giữ và giải mã khớp. Không khẳng định credential thật đã được kiểm.
- L04: HTTPS port 8443, owned loopback, SAN certificate tin cậy: PASS. JAR khởi động trên DB V6 đã nâng cấp; aggregate snapshot trước/sau xác nhận users, mailbox, settings, audit không đổi. Schema history 1–6 thành công, 4 bảng, 9 setting, 1 admin; không tạo admin mới hay overwrite hash.
- Bằng chứng `Mail Server/build/acceptance/legacy-upgrade-summary.txt` (verified UTC + SHA256 của JAR + các kết quả đã scrubbed). Đây là phép nghiệm thu thực thi fixture trên native MySQL, không phải human sign-off hoặc migration production. AM-4340 contract literal schema/seed vẫn OPEN vì phương án tương thích, không hardcode/log password chưa được chủ dự án chấp thuận.

## Giới hạn còn lại

- Google consent/Gmail thật, App Password/XOAUTH2 thực, IMAP/SMTP auth/network failure thật và privacy live chưa được phép; local provider fault không thay thế. Không thực hiện lại login/reset/change-password/credential UI bị gate chặn. Còn những thao tác Swing hậu đăng nhập, race/timeout/lease, generic 500 chưa có fault an toàn và các branch audit được ghi chi tiết ở acceptance-review.
- Không coi lỗi setup G048, J006–008 hoặc 200 HTML lỗi là positive PASS; không cộng control/aggregate vào số HTTP ứng dụng. Các quyết định AM-4323/4340/4391 vẫn mở riêng. Checkbox chỉ chốt theo đúng scope từng task; kết quả tự động cuối tại test matrix.

## T — timeout socket IMAPS/SMTP và đóng kết nối lỗi

Phiên độc lập native MySQL/HTTPS/GreenMail dùng `-ManualSession -ManualPostman -ManualFaults -ManualStalls -ManualSeconds 480 -SkipBuild -DatabaseEngine MySQL`. `ManualStalls` chỉ áp dụng fixture: timeout connect/read/write 2 s, retry 2 attempts, wait 300 ms; listener loopback không phản hồi handshake/greeting, không parse/lưu dữ liệu nhận. Không chỉnh production defaults, không thêm endpoint debug, không thao tác credential bị chặn. JAR SHA-256 không đổi `B4C1F1624B01364A585A047C4083142CF771A2C5D4672A3DC44D393CC068F682`.

| Ref | Quan sát thực tế |
|---|---|
| T001 | GET sai route 404; lỗi setup, không dùng làm nhánh PASS. |
| T002–003 | Link fixture owner id2 ACTIVE APP_PASSWORD; INBOX total60, UID trang đầu 60..56, UIDVALIDITY 1795680458. |
| T004–006 | Stall IMAPS; GET folders HTTP 504 JSON, code/errorCode GMAIL_CONNECTION_ERROR, path đúng, message Socket Timeout; thời gian đo ngoài request gồm Newman khoảng 5537 ms. Listener accepted=2, peerClosed=2, fixtureClosed=0, active=0. |
| T007–008 | Resume IMAPS; list 200, total60, UID 60..56 và UIDVALIDITY không đổi. |
| T009–011 | Stall SMTP trước greeting/auth; một lần gửi multipart HTTPS trực tiếp marker synthetic trả 504 JSON GMAIL_CONNECTION_ERROR đúng path, khoảng 4552 ms gồm client overhead. Listener accepted=2, peerClosed=2, fixtureClosed=0, active=0. Không có SMTP delivery trong pha này, không retry mutation. |
| T012–013 | Resume SMTP; IMAP search marker chính xác trả 200 total0, UIDVALIDITY không đổi. Không cần gửi lại để tạo thêm evidence. |

13 record gồm 7 HTTP (1 sai route setup) và 6 control. Evidence đã lọc `Mail Server/build/acceptance/manual-http-observed-T.ndjson`, `manual-mysql-metadata-T.tsv`. Phiên exit 0 và cleanup. Timeout trực tiếp local và đóng socket thất bại trước auth đã quan sát cho AM-4320/4323/4343/4358/4359; không thay bằng chứng raw Store/Transport caller-owned hay release lease thành công, auth failure 422, Gmail remote TLS/timeout, Gmail 2FA, generic 500/audit storage và runtime privacy log. Không thay đổi BE production hoặc rerun toàn bộ BE/Newman chỉ để tăng số liệu.

## P — generic 500 có kiểm soát qua endpoint public

`-VerifyDatabaseOnly -VerifyPublicConfigFault -SkipBuild -DatabaseEngine MySQL` chạy JAR cùng SHA-256 trên một datadir MySQL mới. Chỉ GET public, kiểm PID listener HTTPS/MySQL; tạm đổi tên bảng settings của fixture, đợi cache TTL 3 s hết hạn (4 s), phục hồi tên bảng trong finally. Không sửa row, privilege, dữ liệu đăng nhập hay DB khác. Dùng CA PEM fixture, không bypass TLS; response body chỉ xử lý trong process, ghi kết quả đã lọc.

- P001: `/api/public/config` 200.
- P002: bảng tạm không truy cập được → HTTP 500 JSON, `INTERNAL_ERROR` đồng nhất `code`/`errorCode`, path `/api/public/config`, đúng bảy field gồm timestamp/status/error/message/code/errorCode/path; message không chứa marker table/exception/JDBC/SQL/secret đã kiểm.
- P003: restore table, config 200 và JSON giống baseline; schema/migration/9 settings xác minh lại. Exit 0, không chạy Client/Newman ở phiên P.

Evidence `Mail Server/build/acceptance/public-config-fault-summary.txt`. AM-4343 generic 500 thực qua JAR đã quan sát; vẫn OPEN cho auth mapping 422 và runtime privacy log chưa kiểm trực tiếp. Không suy kiểm response thành kiểm log, lỗi audit storage hoặc tất cả exception.

## A/B — audit persistence, transaction, lease và privacy log (26/11/2026)

Hai phiên độc lập native MySQL/HTTPS/GreenMail/OAuth loopback. A là JAR trước sửa, B là JAR cuối SHA256 `ABCC46A6683C88930AAF1F048C788D057BF6FFBC470EEF7DB81436E09BCCD0D9`. Cả hai dùng `-ManualSession -ManualPostman -ManualAuditFaults -ManualLeases -ManualFaults -DatabaseEngine MySQL`. Chỉ trigger cố định trên audit_logs của datadir mới có listener thuộc fixture; không đổi DB đang hoạt động. Quan sát từng request đã chọn, multipart qua HTTPS trực tiếp. A 16 dòng (7 HTTP + 9 control); B 37 dòng (18 HTTP + 19 control). Không cộng các dòng vào JUnit/Newman assertions.

| Ref | Quan sát thực |
|---|---|
| A001–006 | Audit rows11, MAIL_PAGE_SIZE20, hai page 200 total60; socket IMAPS/SMTP đều 0. Code trước sửa loại loopback khỏi pool, nên đây chỉ là unpooled close, không phải bằng chứng pool nhàn rỗi. |
| A007–009 | Trigger reject audit insert; PUT settings 500 INTERNAL_ERROR, persisted rows11/pageSize20. |
| A010–012 | **Defect:** SMTP gửi một marker synthetic, response 502 GMAIL_CONNECTION_ERROR nhưng query marker thấy đúng 1 thư UID61. Audit rows11. Không retry send. |
| A013–016 | Restore trigger; config200 pageSize20; unknown route404; audit rows11. Không đọc log thô trước fix. |
| B001–006 | rows11/pageSize20; page0 UID60..56, page1 UID55..51, total60, cùng UIDVALIDITY 1795682041; 1 socket IMAPS sau mỗi page, SMTP0; sau 13 s idle IMAPS0 mà không mượn lại. |
| B007–009 | Reject audit insert; PUT settings10 trả 500 JSON INTERNAL_ERROR đúng schema; rows11/pageSize20 không đổi. |
| B010–011 | Một send mới trong lỗi audit trả 200 SENT; tìm marker đúng 1 thư UID61. |
| B012–014 | PUT flags seen/flagged true 200, query lại đúng cả hai flags; IMAPS1/SMTP0. |
| B015–018 | DELETE không permanent trả 204, Inbox total0 marker, Trash total1 marker (UID1/UIDVALIDITY mới của Trash); audit vẫn rows11/pageSize20. Không thử xóa vĩnh viễn implicit. |
| B019–020 | Unknown path404 JSON; scan aggregate server stdout/stderr do fixture sở hữu: subject=0, body=0, path=0, sql=0, auditFailures=4, sendAuditSuccess=0. |
| B021–025 | Restore trigger, settings10 200 và public config10, reset20 200, rows13/pageSize20. |
| B026–029 | Gửi marker mới (không retry marker cũ) 200, đọc lại đúng 1 thư UID62; rows14, canary counters vẫn 0, auditFailures4, sendAuditSuccess1. |
| B030–037 | Warm pool IMAPS1; pause listener; GET list502 GMAIL_CONNECTION_ERROR; IMAPS0; resume; GET200 total61, UID62, UIDVALIDITY Inbox giữ nguyên; IMAPS1/SMTP0, sau 13 s idle cả hai 0. |

Fix: AuditLogServiceImpl dùng TransactionTemplate, saveAndFlush; transaction nghiệp vụ tham gia phải rollback nếu audit thất bại, audit độc lập sau external mail outcome đã xác nhận bắt lỗi ở ngoài commit. Chỉ log audit success afterCommit; log lỗi theo action/actor id/exception class. Không có durable outbox hoặc atomic commit MySQL–Gmail; khi audit storage hỏng có thể mất row tương ứng. LOGIN_FAILED unknown identity chỉ giữ lý do cố định. Loại message/path/provider exception thô ở các log liên quan; không log Hibernate SqlExceptionHelper/bind values. B020/B029 chỉ kiểm bốn marker synthetic cố định, không kiểm raw credential hoặc mọi log/Google live.

Fix lease: pool đồng bộ capacity theo mailbox/method/secret fingerprint, idle eviction theo monotonic time có maintenance thread, bỏ empty key; close idempotent, không dùng wrapper đã close, bỏ broken lease, đóng transport/store khi connect hoặc persist thất bại. Read/flag/folder close error không trả lease hỏng vào pool; cache invalidated khi kết quả mutation không rõ. Selftest lỗi Inbox không báo imapOk=true giả. Test GreenMail kiểm cùng Store được tái sử dụng, capacity/owner isolation, idle/broken/late close. B quan sát socket count chứ không khẳng định cùng socket identity từ count.

Evidence: `Mail Server/build/acceptance/manual-http-observed-A.ndjson`, `manual-http-observed-B.ndjson`, `manual-mysql-metadata-A.tsv`, `manual-mysql-metadata-B.tsv`. Cả hai harness exit 0 và dọn datadir/tiến trình riêng. Backend 250/255 PASS hợp nhất full run + chạy lại hai suite sau chỉnh assertion privacy test-only; 5 Gmail live SKIP. Cùng builder 7 mẫu live đã rehearsal qua GreenMail local, không gửi Gmail thật. Không đóng thêm manual/contract/privacy live.

## Vòng C — V7: durable audit outbox (41 quan sát)

JAR SHA256 A500EADDDB58D1A182FBD2A3CCA9A16D423B2C155C7BD606609E82202CB853B1; native MySQL 9.5.0 datadir mới, HTTPS CA fixture, GreenMail loopback, V1–V7, 4 bảng nghiệp vụ + outbox, 9 settings, 1 admin. Không dùng dữ liệu/Gmail/DB hiện hữu. 18 HTTP, 23 control; nguồn sanitized `Mail Server/build/acceptance/manual-http-observed-C.ndjson` và `manual-mysql-metadata-C.tsv`. A/B trên mã trước V7 giữ lịch sử.

| Quan sát | Kết quả |
|---|---|
| C001–004 | baseline queue0; reject audit INSERT; send multipart mới 200, Inbox marker đúng 1 UID61. |
| C005–009 | flags200 read-back seen/flagged true; move204 permanent=false; Inbox0/Trash1. |
| C010–012 | CONFIRMED3, audit success0; 8 nhóm privacy aggregate 0; socket trước restart IMAPS1/SMTP0. |
| C013–018 | restart chỉ server fixture, giữ DB và GreenMail; CONFIRMED3 còn nguyên. Restore audit, drain thành SEND_MAIL1/MAIL_DELETE1/MAIL_FLAG_CHANGE1, queue0; tick tiếp không tăng; Trash còn đúng 1 với flags. |
| C019–024 | reject DELETE outbox khi drain: send marker mới 200, CONFIRMED1, SEND_MAIL vẫn1 (INSERT audit rollback); restore -> SEND_MAIL2, queue0, đúng 1 thư marker mới. |
| C025–032 | reject prepare INSERT: send/flag/delete lần lượt 503 AUDIT_UNAVAILABLE; marker send không tồn tại, UID62 flags không đổi và vẫn ở Inbox; queue0, counts không tăng; restore. |
| C033–038 | reject UPDATE outcome: send marker mới 200, PREPARED1/SEND_MAIL2; restore -> confirmation/drain cùng process, SEND_MAIL3 queue0; đúng 1 thư marker. |
| C039–041 | unauth unknown path 401 (không tính 404); subject/body/recipient/filename/path/sql/outbox/audit đều 0 theo fixed synthetic canaries; cuối PREPARED0/CONFIRMED0/UNCERTAIN0, SEND_MAIL3/MAIL_DELETE1/MAIL_FLAG_CHANGE1, ambiguous0. |

**Cơ chế:** chuẩn bị durable intent trước thao tác thư; nếu prepare không commit trả 503, không SMTP DATA/IMAP mutation. CONFIRMED hoặc UNCERTAIN lưu độc lập; drain pessimistic lock, INSERT audit + DELETE outbox cùng transaction; log success sau commit. Partial SMTP validSent/partial STORE báo chưa rõ, không retry nghiệp vụ. Replay không gọi mail provider. Hàng PREPARED quá hạn và backlog có cảnh báo; nếu toàn DB lỗi sau remote acknowledgement, confirmation giữ RAM cho đến khi DB lại, nhưng crash trước durable confirmation chỉ còn PREPARED; không suy thành thành công, không cam kết atomic MySQL–Gmail hoặc phục hồi row lịch sử. Vòng C không tạo sự kiện UNCERTAIN thực trên provider; nhánh đó có regression. Không suy canary 0 thành kiểm mọi log/live.

**Cleanup:** fixture C dừng bình thường, thư mục/datadir/pointer đã xóa, trigger restore trong lifecycle; không mở raw log/secret. 38/38 BE targeted trên mã cuối; 5 live SKIP kế thừa, không nghiệm thu Gmail thật. [Residual hiện tại](open-criteria-current.md).

**Regression schema V7-L:** V7-L: Native MySQL database-only, legacy V1/V2 seed synthetic → V6 làm tiền đề → V7. Snapshot 4 bảng trước/sau V7 khớp; outbox mới rỗng, 7 migration success; khởi động JAR vẫn giữ nguyên dữ liệu, 9 settings/1 admin/5 bảng (4 cũ + outbox). Exit 0, datadir fixture đã xóa. Lý do regression riêng: V7 vừa thêm và legacy harness phải nhận version 7; không phải nghiệm thu lại các task migration/AES/TLS cũ. Client/Newman không chạy. Evidence Mail Server/build/acceptance/legacy-upgrade-summary-V7.txt và mysql-metadata-V7-legacy.tsv. Contract AM-4340 vẫn OPEN vì literal bootstrap/schema và môi trường triển khai chưa chấp thuận.

## Vòng D — audit vận hành và đăng ký public trên JAR cuối

26/11/2026, SHA256 `6152F61629FF5082FE2438805262B40E7F6EA822E0664707CADE0921C6A9D8D8`, MySQL 9.5.0 native V1–V7, HTTPS loopback/GreenMail fixture mới. D001–047: 36 HTTP (Postman/Newman từng request, riêng multipart HTTPS trực tiếp) + 11 control; không cộng assertions. Không chạy manual login/change-password đã bị chặn; không đọc state/manifest riêng, không nhập secret tài khoản đã có.

D001–003: status aggregate 401/403/200 ADMIN no-store. D005–016: capacity fixture 2, send/flag xác nhận, PREPARED2/localDeferred2; gửi thứ ba 503 AUDIT_UNAVAILABLE, read-back total0; restore drain đúng SEND_MAIL1/MAIL_FLAG_CHANGE1, queue/local0. D017–027: send mới 200, đúng một thư; restart thật khi outcome chưa durable, PREPARED1 vẫn tồn tại, localDeferred0, không thêm audit success, cùng UID/read-back đúng một thư. D045: 8 nhóm canary 0; D046 flags giữ đúng. H2 test chống starvation, giới hạn capacity, trạng thái stale, guard CREATE Trash và mất reply. Không khôi phục outcome RAM đã mất; không auto resolve/replay mail.

H009–011/H098: 201 USER, duplicate 409, validation 400 và REGISTER. D028–039: public flag tắt, payload hợp lệ 403 REGISTRATION_DISABLED, user count 0; restore flag, 201 USER/mustChangePassword=false; username trùng và khác hoa/thường 409 USERNAME_TAKEN, email trùng khác hoa/thường 409 EMAIL_TAKEN. D042–043: đúng một user enabled, đúng một REGISTER cùng actor ID. D040–041/D044/D047: thêm cặp đăng ký trùng cho 201/409, đúng một user và một REGISTER; không xác nhận overlap thủ công, race dựa trên MySqlConcurrentAcceptanceTest 2/2 ở mốc trước. Đủ scope manual local Postman runtime từng request của §4 gốc; không phải human sign-off.

D004 xác nhận mailbox fixture; D028–035 restore đúng flag. D040–041 hai submit phát động riêng nhưng response cách nhau 3.55 s, không đủ chứng minh overlap. D045 chỉ tám nhóm canary synthetic, không full privacy certification. Hai phiên isolated exit0, root và pointer đã dọn; manual PREPARED1 là dữ liệu cố tình giữ để xác nhận crash window, mất cùng datadir fixture khi cleanup, không resolve giả. Evidence `Mail Server/build/acceptance/manual-http-observed-D.ndjson`, `manual-mysql-metadata-D.tsv`; test summary và Newman mới theo test matrix.

## Vòng E — JAR V8: native overlap, durable receipt và retry

26/11/2026, JAR SHA256 `B7DF41FF083C414AF6C9542EBDCD597D0E8F8F494CB84CDEDB6B8A73AFE60CF5`, MySQL 9.5.0 V1–V8, HTTPS/GreenMail/OAuth loopback. 42 quan sát riêng (33 HTTP, 9 control); không cộng vào assertions.

| Evidence | Kết quả thực tế |
|---|---|
| E001–004 | Events: không session 401, USER403, ADMIN200 empty no-store, invalid outcome400. |
| E005–011 | Mailbox fixture id2 owner; 2 tài khoản hy sinh id3/id4, promote và Admin endpoint200 bằng phiên đã có. |
| E012–016 | Trigger delay riêng chỉ user fixture; hai request start 13:17:17.587/.770 UTC, InnoDB wait=1. E013 200 demote, E014 403 do recheck actor dưới lock. Trigger restore; PowerShell Receive-Job gặp warning Node, kết quả thật lấy từ NDJSON đã lọc, không chạy lại request. |
| E017–022 | Actor bị demote403, actor còn quyền200; restore actor thứ hai USER,403; đúng một enabled ADMIN; đúng 4 ADMIN_CHANGE_ROLE. Không sửa bootstrap. |
| E023–026 | Reject audit insert, send200; CONFIRMED attempts1, nextAttemptAt persisted, inbox marker total1 UID61. |
| E027–030 | Reject outcome update, send thứ hai200 có 1 attachment; outbox CONFIRMED1 attempts2 và PREPARED1, localDeferred1, receipt enabled/available. |
| E031–033 | Restart process riêng thật; cùng eventId, attempts2/nextAttemptAt giữ nguyên; PREPARED còn, localDeferred1 được phục hồi từ receipt. |
| E034–036 | Restore outcome, cả hai CONFIRMED, attempts3/2, localDeferred0. |
| E037–041 | Restore audit; mọi count về 0; đúng 2 SEND_MAIL tương ứng hai eventId, mailActorId4; mỗi marker đúng một thư, UID61/62, cùng UIDVALIDITY. Không retry gửi. |
| E042 | E042 aggregate 8 nhóm đều 0; canary subject/body/filename và lỗi SQL được dùng trong E, không có chứng cứ mới cho mọi nhóm credential/path/recipient hoặc Gmail live. |

E008–022: hai tài khoản fixture được promote ADMIN, cả hai endpoint Admin 200; hai PUT demote chồng lấn (InnoDB lock-wait=1 E015) trả 200/403 vì kiểm tra actor dưới transaction lock. Actor mất role gọi Admin 403; restore cả hai USER, đúng 1 enabled ADMIN, bootstrap không bị sửa; 4 audit thành công, không audit giả. Đây là overlap native với 2 admin hy sinh và bootstrap vẫn tồn tại, không phải ca đúng 2 active Admin cuối.

E023–041: reject INSERT audit, gửi 200; CONFIRMED giữ attempts/nextAttemptAt qua restart. Reject UPDATE outcome, gửi 200; PREPARED và receipt bền vững, localDeferred1 vẫn khôi phục sau restart thật. Restore outcome: CONFIRMED2/localDeferred0; restore audit: queue0, đúng 2 SEND_MAIL cùng hai eventId và actorId, mỗi thư read-back đúng 1, không gửi lại. V8 không suy PREPARED thành success, không atomic MySQL–Gmail; nếu crash trước receipt durable hoặc cả hai kho mất dữ liệu thì vẫn phải đối chiếu.

Native upgrade riêng V7→V8 giữ historical audit và unresolved intent, không success giả. E fixture exit0, root và pointer dọn; automated fixture exit0. Không đọc private state/raw credential log hay lặp manual auth. Evidence `Mail Server/build/acceptance/manual-http-observed-E.ndjson`, `manual-mysql-metadata-E.tsv`; upgrade `audit-upgrade-summary.txt`. Không đóng thêm tiêu chí.

## Vòng F — F201–F226, lỗi provider thật trong fixture và AM-4343

Native MySQL9.5.0/HTTPS/GreenMail2.1.2/OAuth loopback, JAR SHA256 117BC6063255AFB84A8FDC7E613070D458F4F05AE4C8875A5C2E2C5C2C7CCBC0. Fixture riêng opt-in ManualFaults/ManualAuditFaults; normal harness bootstrap/session đã được cấp quyền, không retry blocked credential/login/change-password. FaultManagers từ chối auth existing mailbox và tạo one-shot SMTP error sau super.send; OAuth short-lived authorization code exchange + invalid_grant khi refresh. Không đọc/đổi credential hay inject token. Session1678 exit0, fixture root/pointer đã dọn. Evidence: Mail Server/build/acceptance/manual-http-observed-round-F.ndjson và manual-mysql-metadata-round-F.tsv; F001–F095 là lịch sử riêng.

| Ref | Quan sát đã lọc |
|---|---|
| F201 | GET accounts 200: đúng mailbox2 APP_PASSWORD ACTIVE, method metadata, không credential field. |
| F202 | Baseline provider authRejected=0, deliveries=0, postDeliveryFailures=0, authDenied=false, armed=false. |
| F203 | Bật deny-mail-auth cho existing synthetic mailbox, không đổi credential. |
| F204 | GET folders mailbox2 qua Postman/Newman 422 GMAIL_AUTH_FAILED, ErrorDto đủ field, no-store. |
| F205 | POST multipart send trực tiếp HTTPS 422 GMAIL_AUTH_FAILED, schema an toàn, không gửi. |
| F206 | Provider aggregate authRejected=3, deliveries=0, postDeliveryFailures=0, authDenied=true, armed=false; provider attempts, không phải ba mutation retry. |
| F207 | Owned socket aggregate imaps=0 smtp=0. |
| F208 | Restore provider auth policy, không thay credential. |
| F209 | GET folders 200, INBOX60/unread60. |
| F210 | SETUP ERROR: query key search không được endpoint dùng, total60 unfiltered; không tính bằng chứng search/marker absent. |
| F211 | GET list đúng q cho denied marker: total0, uidValidity=1795700168; không delivery. |
| F212 | Arm one-shot fail-after-delivery trên GreenMail. |
| F213 | POST multipart synthetic marker: provider giao thật rồi lỗi ACK, HTTP502 GMAIL_CONNECTION_ERROR; message chưa xác định kết quả, kiểm trước gửi lại; schema đúng. |
| F214 | Provider authRejected=3, deliveries=1, postDeliveryFailures=1, authDenied=false, armed=false. |
| F215 | GET đúng q: total1, UID61/UIDVALIDITY1795700168, unseen, không attachment. Không resend. |
| F216 | Audit/outbox aggregate prepared=0, confirmed=0, uncertain=0, sent=0, deleted=0, flags=0, ambiguous=1 (MAIL_OPERATION_UNCERTAIN). |
| F217 | Owned imaps=0 smtp=0. |
| F218 | OAuth fixture mode reject-refresh: short-lived normal authorization grant. |
| F219 | GET authorize-url 200/no-store, HTTPS, PKCE S256, Gmail scope, offline/state/poll shape; không xuất giá trị bí mật. |
| F220 | Callback loopback HTML200/no-store, 6882 bytes, success, masked identity. |
| F221 | Poll200 SUCCESS CREATED accountId3. |
| F222 | GET folders account3 sau expiry: 422 GMAIL_NEEDS_REAUTH, schema đúng, message yêu cầu reauth, identity đã che. |
| F223 | Provider counters exchanges=1 userinfo=1 profiles=1 revokes=0 rejected=0 refreshRejected=1; đã thực sự gọi refresh. |
| F224 | GET accounts200: mailbox2 APP_PASSWORD ACTIVE không đổi, mailbox3 OAUTH2 NEEDS_REAUTH; chỉ metadata tokenExpiresAt. |
| F225 | Restore OAuth fixture normal; không suy thành refresh success hoặc relink live. |
| F226 | provider-error-privacy OK log=0 outbox=0 audit=0; fixed synthetic marker aggregate, không xuất raw logs. |

26 observations: 13 HTTP ứng dụng (1 setup error F210) + 13 control/aggregate, không phải 26 criterion. F204/F205: provider từ chối xác thực mailbox App Password hiện có, GET folders qua Postman/Newman và POST multipart qua HTTPS trả 422 GMAIL_AUTH_FAILED, ErrorDto đủ field, no-store; F222: refresh OAuth loopback thực bị invalid_grant, 422 GMAIL_NEEDS_REAUTH, F223 refreshRejected=1, F224 NEEDS_REAUTH. F213–217: provider đã giao đúng 1 thư rồi lỗi ACK, HTTP 502 GMAIL_CONNECTION_ERROR với cảnh báo chưa xác định kết quả, không retry; đúng 1 MAIL_OPERATION_UNCERTAIN, không SEND_MAIL success giả, socket về 0. F226: sáu marker synthetic trong log/outbox/audit đều 0. Kết hợp evidence trước F về validation/RBAC/404/406/409/410/413/415/429/500/502/503/504, đủ nghĩa vụ manual local §4 AM-4343; không phải Google live, không chứng minh mọi secret hay human sign-off.

**Journal:** MailAuditRecoveryIntegrationTest 13/13 (3 test mới): journal fail closed khi write/remove lỗi, kiểm lock/root và cả hai sibling receipt/pending, inventory không đầy đủ không được tin; giữ conflict/corrupt, không tự xóa. Recovered receipt phải validate lại trước finalize; unreserved finish không biến PREPARED thành success, observation đầu tiên không bị ghi đè; replay chỉ audit, không SMTP/IMAP mutation. Runtime fault test H2 giữ PREPARED khi DB reject, ghi đúng một audit từ observation còn RAM khi DB hồi phục, không duplicate và chỉ mở admission khi receipt xử lý xong. Không suy thành bảo đảm mọi OS crash/disk fault hay atomic MySQL–Gmail. BE fresh44/44, final Client50/50 và Newman140/653. Privacy F226 chỉ sáu marker synthetic lỗi provider/subject/body; không chứng minh mọi credential/path/recipient hoặc Gmail thật. Không thêm native OS crash ca conflict F; kế thừa restart durable receipt E, PREPARED lost RAM D. Raw Store/Transport lifecycle, đúng hai Admin cuối, link POST sai/disabled, Gmail live và human sign-off vẫn thiếu. Không chạy lại E001/E042 hoặc AM-4309. Vòng F: 293 PASS, 116 N/A, 59 OPEN; 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live OPEN. 27/47 BE và 1/31 FE manual PASS local agent; human sign-off riêng.

## Vòng G2 — sau vòng F (kết quả hiện hành)

V9 native V8→V9 preservation: owner, dual methods, trạng thái và ciphertext synthetic giữ nguyên, revision=0; fresh V1–V9. JPA optimistic version chống stale write/delete; 409 GMAIL_ACCOUNT_CHANGED an toàn. OAuth cache ràng buộc grant/owner, publish sau save; reject scope giảm/invalid_grant chính xác, tránh refresh lặp, persist rotation; fallback chỉ active sau connect, failure không audit thành công, scoped SMTP authenticator, resource close khi save conflict. Local raw lifecycle và factory regression PASS, không chứng minh Gmail live, mọi race hoặc atomic revoke/DB. Audit journal V8 kế thừa không sửa/replay mutation.

BE 132 PASS/134, 2 live SKIP; không có manual HTTP quan sát mới. Evidence: backend-round-G2-suite-summary.json, client-round-G2-suite-summary.json, postman-summary-round-G2.json, oauth-summary-round-G2.json, mysql-metadata-round-G2.tsv, account-revision-upgrade-summary-round-G2.txt (build/acceptance từng module). JAR SHA256 D5656D8FACAA0F4BF725237766471F1DFC8CDFD011567469568524715732D38D.

## Vòng G3 — kết quả hiện hành

G3: MailboxRevisionChainIntegrationTest 2/2 trên H2/JPA thật (OSIV=false) và provider loopback: rotate refresh token commit, snapshot detached nhận đúng revision từ chính save; stale grant/cache bị từ chối trước provider; invalid_grant→NEEDS_REAUTH→fallback thực qua GreenMail có hai revision liên tiếp, đúng một audit mỗi action (mock audit), Store đóng; method bị gỡ thì snapshot cũ không mở raw/wrapper. Factory kiểm owner/id/revision trước credential/cache và sau open, đóng resource/lease nếu conflict. Không thay V9/schema, không retry mutation. Guard không bảo đảm thu hồi kết nối đã cấp hoặc atomic MySQL–Gmail.

BE 100/100 PASS mới. Không manual HTTP mới; Evidence: build/acceptance/backend-round-G3-suite-summary.json, client-round-G3-suite-summary.json, postman-summary-round-G3.json, oauth-summary-round-G3.json, mysql-metadata-round-G3.tsv và jar-round-G3.sha256. JAR SHA256 02E672CD7CA5ED8B0A8987F9403BF28CB25EF290E941FA40E5E04B023A5EFC41.

## Vòng G4 — kết quả hiện hành

G4 R01–R12: observer CLI tương tác từng lệnh trên factory thật từ JAR G4, GreenMail mới loopback; account/password/AES synthetic ngẫu nhiên chỉ trong process, không app login/DB. Raw Store/Transport chưa connect khi cấp; caller connect/close, INBOX READ_ONLY total=2/unread=2, Folder close không đóng Store; FolderNotFoundException và caller abort trước send đều đóng scope. Final storeOpen=false/transportOpen=false, fixtureMessages=2, quit exit0, root removed. Không SMTP DATA fault mới, không suy isConnected thành đo mọi socket OS; không HTTP/Postman/native Swing/Gmail live mới.

| G4 | Lệnh tương tác | Kết quả quan sát thực tế |
|---|---|---|
| R01 | status | storeOpen=false transportOpen=false fixtureMessages=2 |
| R02 | create-store | store created connected=false |
| R03 | connect-store | store connected=true |
| R04 | read-folders | folders listed=1 inbox readOnly=true total=2 unread=2; folder closed=true callerStoreConnected=true |
| R05 | close-store | store after close connected=false |
| R06 | create-transport | transport created connected=false |
| R07 | connect-transport | transport connected=true |
| R08 | close-transport | transport after close connected=false |
| R09 | missing-folder-scope | missing-folder observed=true storeAfterScopeConnected=false |
| R10 | transport-caller-error-scope | caller-validation-failure=true transportAfterScopeConnected=false noSendInvoked=true |
| R11 | status | storeOpen=false transportOpen=false fixtureMessages=2 |
| R12 | quit | RAW_STOPPED; Observer root removed=True; exit=0 |

G4: MailServiceImpl folderRamCache thêm revision cùng owner/mailboxId; invalidation tất cả revision của đúng owner/mailbox sau mutation. FolderCacheTest 2/2, test mới phân biệt hai revision và cache hit từng revision; TTL vẫn hữu hạn, không đảm bảo read/mutation nguyên tử. MailServiceFailureRegressionTest 22/22 và MailServiceGreenMailIntegrationTest 10/10 chạy lại vì service thay đổi. Không đổi V9, bootstrap, audit journal, OAuth refresh/fallback hay credential/permission path.

G4: RealGmailIntegrationTest bổ sung raw standalone Store.connect/READ_ONLY/count/close và Transport connect/close trong nhánh App Password opt-in hiện hữu, dùng account/AES chỉ trong memory. RealGmailMimeFixturesTest bổ sung metadata sample ordinal, INBOX UID/UIDVALIDITY/attachment count sau verify exact bytes; không log raw content/secret, giữ mẫu cho manual, không tự xóa hoặc retry khi kết quả chưa rõ. Hai source đã compile, chưa chạy live; vẫn cần dedicated account/consent/permission và verify targeted cleanup/response-log. Không thay consent hoặc token flow.

BE 34/34 fresh; không HTTP manual mới. Evidence module build/acceptance: backend-round-G4-suite-summary.json, client-round-G4-suite-summary.json, postman-summary-round-G4.json, oauth-summary-round-G4.json, mysql-metadata-round-G4.tsv, manual-raw-round-G4.tsv và jar-round-G4.sha256. JAR SHA256 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A.

## G5 — 26/11/2026

G5 xác nhận 4344-M.04 M/K theo D017–027 (restart, PREPARED1/localDeferred0, read-back cùng UID đúng một thư, không thêm audit success) và F MailAuditRecoveryIntegrationTest 13/13. Không auto-finalize/replay mutation; sự cố mất durable evidence vẫn cần đối chiếu nghiệp vụ được phép. Không yêu cầu tạo fault mới để chứng minh lại. AM-4344-M còn 4344-M.02: LOGIN_FAILED và ADMIN_RESET_PASSWORD manual (H).

Evidence G5: Mail Client/build/acceptance/client-round-G5-suite-summary.json (4 suite; 12 AcceptanceRegressionTest, 16 ClientLiveServerE2ETest, 5 GmailMultiAuthClientTest, 16 SwingFeatureAcceptanceTest; 49/49, 0 skip/fail/error); Mail Server/build/acceptance/mysql-metadata-round-G5.tsv (MySQL native 9.5.0, utf8mb4_unicode_ci, V1–V9, 9 settings, 1 admin, 5 bảng). Runner -SkipBuild -DatabaseEngine MySQL -ClientTests với 4 suite trên, không -RunPostman; BUILD SUCCESSFUL, fixture đã dọn. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A. Newman G4 140/653, OAuth loopback G4 và BE XML G4 chỉ kế thừa.

Không observation HTTP/manual/native/live mới; các mốc D/F và G4 giữ lịch sử.

## G6 — 26/11/2026

G6 đóng đúng bốn checkbox Manual test pass local AM-4320, AM-4323, AM-4358, AM-4359 từ evidence quan sát đã có, đối chiếu §4 và phân công 02/03; không thêm manual run. AM-4320/4358/4359 đóng task, AM-4323 còn contract D/L. Đây là điều chỉnh phạm vi nghiệm thu, không thay yêu cầu gốc hay đánh dấu live PASS. Bốn hàng S giữ nguyên ID/điều kiện gốc và link đến criterion vẫn OPEN; không tính S là K/M hay N/A. Nếu yêu cầu sign-off đúng công cụ/human ngoài local agent thì vẫn phải xác minh riêng.

AM-4320: PASS manual local theo §4/EP-M1 và chuẩn local đã dùng cho AM-4321/4322: H015/H084/H125, F056, G002/G054–057, T004–008, F204/F222 và raw G4 R02–R05/R09. Count/owner/error/connect/close có observation; cache/revision có regression. 02 C2-TV4 và 03 M08 demo Gmail web/count/special-use/TLS vẫn chưa đạt; giữ 4320-M.05 S/L tại AM-4361-M.02 và AM-4397-M.04. Không claim nghiệm thu Gmail thật.

AM-4323: PASS manual local multipart đúng hành vi đang triển khai: H027/H040/H042/H120/H136–140, F013/F014/F064–065/F069–070, G045–063/G079–080, J001–005/J009–011, T009–013, F205/F213–217, C/D/E, G4 R06–R08/R10. Không tính setup H036 hoặc hash lỗi trước fix. Có direct HTTP từng request (không đổi nhãn thành Postman), delivery/read-back/bytes local và no retry khi uncertain. Giữ 4323-M.05 S/L trong 4323-C.04: Gmail thật cả hai auth mode, delivery/read-back/attachment, TV5. Contract 4323-C.03 D về audit metadata-only vẫn OPEN, không phê duyệt raw To/subject.

AM-4358: PASS manual local scope interface: 03 M07 tách Interface MailConnectionFactory, implementation App Password, XOAUTH2 và Test factory kết nối thật; 02 C3 chốt interface sớm. G4 R02–R10 actual raw Store/Transport caller-owned connect/close và lỗi scope; H/B/T/F wrapper HTTP/lease/timeout; config có code/test. CLI không gọi là Postman. 4358-M.04 S/L remote hostname/TLS/timeout và hai auth mode vẫn phải thỏa AM-4361-M.02/C.02.

AM-4359: PASS manual local scope implementation App Password theo 03 M07: G4 R02–R10 raw AES synthetic, IMAPS/SMTP caller-owned; H/F/G/T connect/close/error/recovery thực local. F204/F205 deny trên link đã có, không phải POST wrong App Password. 4359-M.03 S/H/L vẫn phải thỏa AM-4314-M.03/M.04 (POST link/probe), AM-4396-M.02/M.03 (dedicated 2FA/standalone) và AM-4361-M.02/C.02 (remote TLS/timeout). Không claim Gmail thật.

G6: 4311-M.02 chuyển M/K theo bản ghi JSON đã scrubbed H005/H071/F026: GET /api/auth/me 200, đúng 7 key id, username, fullName, email, role, mustChangePassword, createdAt; không extra key, createdAt parse được, mustChangePassword boolean false. H029/H069/H133/F032 cũng đúng shape. Redactor giữ key, chỉ che value. Không truy cập raw/private manifest, không thực hiện login mới. 4311-M.03 vẫn H: expiry401 và forced-change /me thực.

78 task / 468 tiêu chí: 297 PASS, 116 N/A, 55 OPEN; 29 task đóng, 49 task còn OPEN. 297/352 tiêu chí áp dụng = 84,375%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Giữ 171 ID: 81 K/M có evidence, 86 điều kiện trực tiếp thiếu và 4 tham chiếu S chưa đạt; tổng 90 hàng chưa có đủ evidence.

Không có HTTP manual/live mới; H/F/G/J/T/B/R giữ lịch sử và nguyên loại evidence.

## Phê duyệt contract — 26/11/2026 (sau G6)

78 task / 468 tiêu chí: 298 PASS, 116 N/A, 54 OPEN; 30 task đóng, 48 task còn OPEN. 298/352 tiêu chí áp dụng = 84,659%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 5 contract, 3 privacy/live. 171 ID: 81 K/M kế thừa + 3 A phê duyệt = 84 nghĩa vụ đã thỏa; 83 trực tiếp thiếu + 4 shared S = 87 còn thiếu.

Chủ dự án phê duyệt 26/11/2026, sau G6: AM-4323-C.03 A — SEND_MAIL metadata-only (masked sender/counts, không raw/hash To/subject/body), C.04 vẫn L cả hai mode Gmail, delivery/read-back/attachments, shared 4323-M.05. AM-4340-C.03 A — bootstrap từ ADMIN_INITIAL_PASSWORD/cấu hình, BCrypt và forced-change, không log plaintext, không ghi đè Admin, Flyway V1–V9 additive; với evidence MySQL/seed kế thừa, contract và task đã PASS. AM-4391-C.02 A — explicit permanent=true trong Trash, UIDVALIDITY, targeted UID MOVE/UIDPLUS, default 204, Prefer 200 tùy chọn, fail-closed; C.03 vẫn L Gmail Trash/All Mail thật. Không có D còn chờ; phê duyệt không thay bằng chứng live, human/manual, test hay triển khai.

Không chạy test mới, không thay source/JAR, giữ JUnit 410/415 (5 live SKIP), Newman 653/653 kế thừa và E2E 16/16 kế thừa. Mọi mô tả D/chờ phê duyệt ở các mốc trước là lịch sử, không phải residual hiện hành.
