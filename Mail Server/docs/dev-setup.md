# Thiết lập môi trường kiểm thử và nghiệm thu

## Kiểm thử tự động không dùng Gmail thật

Yêu cầu JDK 21, Gradle wrapper có trong hai module. Chạy tuần tự để hạn chế RAM/CPU. Từ thư mục Mail Server:

```powershell
Remove-Item Env:MAILAPP_REAL_GMAIL,Env:MAILAPP_REAL_GMAIL_XOAUTH2 -ErrorAction SilentlyContinue
.\gradlew.bat test bootJar --rerun-tasks --max-workers=1 --no-daemon --console=plain
```

Sau khi build thành công, chạy Client với HTTPS/H2 cô lập (mặc định):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-client-e2e.ps1 -Java21Home $env:JAVA_HOME -SkipBuild
```

Script tạo PKCS12/certificate và thông tin đăng nhập tạm riêng, database mới, cổng loopback ngẫu nhiên và cấu hình Google fixture. Test vòng đời mail gửi, đọc, tải tệp, cập nhật cờ và xóa thư thử nghiệm chỉ trong GreenMail cục bộ; không gửi tới Gmail thật, không dùng database hoặc hộp thư hiện có. Script dọn tiến trình/tệp tạm do nó tạo. Kết quả XML tại `build/test-results/test` của mỗi module. Đối chiếu `docs/testing/test-matrix.md`.

## Nghiệm thu HTTP, Swing và MySQL native cô lập

Yêu cầu MySQL Server binaries (`mysqld.exe`, `mysql.exe`), Node.js/npm (nếu chạy Newman), JDK 21 và desktop headful (cho Swing). Không cần Docker hay khởi động dịch vụ MySQL hiện hữu. Từ Mail Server:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-client-e2e.ps1 -Java21Home $env:JAVA_HOME -SkipBuild -DatabaseEngine MySQL -RunPostman
```

Nếu không tự tìm được binaries, thêm `-MySqlBin '<thu-muc-bin-MySQL>'`. Script tạo datadir mới, kiểm quyền sở hữu listener PID, chỉ bind loopback, chạy migration/seed, Client và GreenMail, rồi Newman 6.2.1 (cài vào thư mục tạm riêng, không cài global; cần kết nối npm registry) và OAuth provider loopback. Bốn lần đổi code PKCE, UserInfo/profile và hai lần revoke được kiểm đếm độc lập. Newman xác minh certificate riêng, không dùng `--insecure`. Báo cáo HTTP/OAuth rút gọn ở `Mail Server/build/acceptance/`; không xuất token hay response thô. `-VerifyDatabaseOnly` kiểm startup/schema/seed riêng, không chạy Client/Newman. `-ClientTests` chọn suite cụ thể; khi chạy `-RunPostman` cần bảo đảm test đổi mật khẩu admin bootstrap đã chạy.

Các kịch bản manual còn lại theo đúng task: `architechture/docs/backend-acceptance-cases.md` (47 BE) và `architechture/docs/swing-acceptance-cases.md` (31 FE). Newman và các test Swing EDT chạy UI/server thật nhưng là tự động, không tự chứng nhận thao tác tay, Google consent hay Gmail thật. Khi nghiệm thu thủ công, chỉ dùng fixture riêng với account/cert/config tạm như hướng dẫn; không dùng instance đang phục vụ dữ liệu thật.

### Phiên fixture cho nghiệm thu từng request / Swing thật

Từ thư mục Mail Server, mở một PowerShell riêng. Dùng JAR đã build đúng bản cần nghiệm thu; `-SkipBuild` không cập nhật JAR cũ. Ví dụ:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-client-e2e.ps1 -Java21Home $env:JAVA_HOME -SkipBuild -DatabaseEngine MySQL -ManualSession -ManualPostman -ManualSeconds 1800
```

- `-ManualSession` độc lập với `-RunPostman`, `-ClientTests`, `-VerifyDatabaseOnly`: không chạy suite tự động; tạo HTTPS, database, GreenMail, OAuth provider loopback và user riêng, config/download dir riêng cho Swing. Thời gian giới hạn 60–3600 giây (mặc định 1800). Chỉ một session đang hoạt động.
- `-ManualPostman` cài Newman 6.2.1 **chỉ trong thư mục tạm của phiên**, cần quyền truy cập npm registry; không thay Postman profile/global npm. Bỏ switch này khi không cần hoặc không được phép dùng mạng; helper gửi HTTPS trực tiếp. Multipart nhị phân luôn dùng HTTPS trực tiếp, kể cả khi bật ManualPostman. Cả hai xác minh cert fixture, không dùng trust-all.
- Khi báo "Manual session ready", từ PowerShell thứ hai dùng `scripts/manual-http.cjs` theo [docs/http/README.acceptance.md](http/README.acceptance.md#manual-session-từng-request). Chỉ đọc pointer để lấy đường dẫn manifest, truyền đường dẫn vào helper; không mở/in manifest, state, credential hoặc log thô. Helper giữ bí mật trong thư mục tạm, ghi NDJSON đã khử nhạy cảm. Mỗi lệnh là một thao tác riêng và đọc kết quả, không phải collection assertion. Với Swing, chỉ mở Client dùng config riêng do fixture tạo, không dùng phiên/config cá nhân; ghi nhận thao tác UI thực riêng.
- Khi hoàn tất, yêu cầu session đang sở hữu dừng qua file marker `stop-manual` trong root fixture (hướng dẫn bên dưới README), hoặc chờ giới hạn thời gian. Harness copy NDJSON đã khử nhạy cảm và metadata MySQL nếu có sang `build/acceptance/`, dọn child process/database/config/secret tạm do chính nó tạo. Không giữ hoặc chia sẻ private manifest/state. Không chạy đồng thời collection tự động trên cùng fixture.

Quan sát HTTP thủ công do agent điều khiển xem `architechture/docs/backend-manual-observations.md`; không suy ra human signoff hoặc Gmail thật từ kết quả local.

## Gmail test thật (AM-4361, AM-4396, AM-4397)

1. Chủ dự án chuẩn bị một tài khoản Gmail chuyên dùng cho test (không có dữ liệu cá nhân/production), bật xác minh hai bước, tạo App Password. Không gửi mật khẩu hoặc token vào chat, Git, ảnh chụp màn hình hay log.
2. Từ Mail Server, chạy `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-real-gmail.ps1`. Script yêu cầu xác nhận TEST-ACCOUNT và nhập App Password ẩn. Hai test kiểm IMAPS/SMTP, gửi một thư tự gửi có UUID và Message-ID riêng, kiểm tra đúng byte/SHA-256 và vòng đời Trash đúng UID; không expunge toàn hộp thư. Script kiểm tra XML để không tính SKIP là PASS.
3. Thêm `-PrepareMimeFixtures` vào lệnh trên, xác nhận thêm KEEP-FIXTURES để chạy `RealGmailMimeFixturesTest`: tạo 7 thư mẫu riêng (plain text; HTML-only; multipart/alternative; PDF hợp lệ nhỏ; PNG/text nhiều attachment; UTF-8 tiếng Việt; message/rfc822 lồng nhau), kiểm tra qua MailService đọc và tải đúng byte. Test giữ lại 7 thư để nghiệm thu tay. Tìm đúng marker UUID `MailApp-MIME-...` được in trong JUnit XML, dùng UID/UIDVALIDITY để đọc và tải; sau khi nghiệm thu, chỉ chuyển/xóa đúng các thư có marker đó. Không dọn cả hộp thư. Nếu gửi hoặc nhận dở dang, marker vẫn dùng để nhận diện phần đã tạo.
4. Đối với XOAUTH2, cần access token ngắn hạn của chính tài khoản test, có scope `https://mail.google.com/`, lấy qua consent Google hợp lệ. Chỉ cung cấp thông qua môi trường tiến trình test cô lập bằng cơ chế quản lý bí mật của người nghiệm thu; không ghi vào command history hay .env. Suite `RealGmailXOAuth2IntegrationTest` yêu cầu `MAILAPP_REAL_GMAIL_DEDICATED=TEST-ACCOUNT`, `MAILAPP_REAL_GMAIL_XOAUTH2=1`, `GMAIL_TEST_EMAIL` và `GMAIL_TEST_OAUTH2_ACCESS_TOKEN`; chạy `./gradlew test --tests "*RealGmailXOAuth2IntegrationTest" --rerun-tasks --max-workers=1 --no-daemon`. Suite có hai test, cùng kiểm IMAPS/SMTP và vòng đời thư tự gửi như App Password. Có thể chọn thêm `--tests "*RealGmailMimeFixturesTest"` nếu đã đồng ý giữ thư mẫu và đặt `MAILAPP_REAL_GMAIL_FIXTURES=KEEP-FIXTURES` (chỉ bật đúng một auth mode). Chưa có runner prompt riêng cho XOAUTH2.
5. Nghiệm thu consent/profile/revoke thật trên **instance riêng**: OAuth client Google riêng dành cho tài khoản test; đăng ký redirect URI HTTPS loopback khớp cổng instance; chứng chỉ riêng được client tin cậy, không tắt TLS. Tạo database tạm riêng, khóa AES/JWT và admin password mới, tắt dotenv/dev seed; không dùng fixture OAuth URL loopback giả trong script E2E cho bước này. Đăng nhập user test, mở authorize URL từ app, xác nhận đúng tài khoản test và scope; ghi nhận callback/poll hoàn tất, email profile khớp hộp thư, mở thư và gửi/tải mẫu. Thực hiện lại với cancel/thiếu scope, kiểm tra không tạo liên kết sai; thu hồi OAuth trên Google, kiểm trạng thái NEEDS_REAUTH sau khi cần làm mới token, rồi cấp lại và kiểm recovery. Cuối cùng gỡ riêng phương thức OAuth2 hoặc unlink tài khoản test, kiểm tra quyền app đã mất trên Google và local state đúng. Không ghi code/state/pollToken/access token/refresh token hoặc ảnh chứa chúng. Instance OAuth thật, thao tác consent/revoke và kết quả Gmail thật vẫn cần chủ dự án thực hiện; fixture mock không thay thế bằng chứng này.

Các biến trong .env.example là tên/placeholder, không phải credential. Nếu không đủ điều kiện thì ghi BLOCKED/SKIPPED, không suy diễn kết quả Gmail thật.

## MySQL

### Kiểm chứng legacy và TLS/8443 riêng

Sau khi build JAR đúng revision, từ Mail Server:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-client-e2e.ps1 -Java21Home $env:JAVA_HOME -SkipBuild -DatabaseEngine MySQL -VerifyDatabaseOnly -VerifyLegacyUpgrade -VerifyConfiguredPort
```

`-VerifyLegacyUpgrade` chỉ dùng với native MySQL fixture mới, database-only. Chạy migration thực V1/V2 từ JAR rồi tạo dữ liệu synthetic; nâng V3–V6, kiểm preservation user/hash/link/owner/ciphertext/setting/audit, reject constraints, và schema conflict riêng để chứng minh V5 fail trước DDL. Kiểm snapshot trước/sau startup, không xuất dữ liệu nhạy cảm. `-VerifyConfiguredPort` kiểm 8443 chỉ khi trống, bind loopback và xác nhận PID sở hữu, kiểm certificate SAN/hostname bằng PEM fixture mới. Nếu port có listener, script từ chối; không dừng hay dùng server khác. Có thể bỏ switch port để chỉ kiểm legacy ở port ngẫu nhiên. Kết quả `build/acceptance/legacy-upgrade-summary.txt` kèm JAR SHA256. Không chạy trên DB/volume triển khai thật.

### Fault fixture độc lập

`-ManualFaults` chỉ kết hợp `-ManualSession`. Tạo giới hạn tải 12 cho phiên synthetic, cho phép `manual-http.cjs mail-fault` pause/resume IMAPS hoặc SMTP mà không reset mailbox; `oauth-fault` chọn `missing-scope`, `profile-mismatch`, `reject-revoke`, `normal`. `provider-status` đọc 5 bộ đếm aggregate. Chỉ sử dụng trên phiên được phép có `-ManualFaults`; chờ ACK, nếu fault/timeout chưa rõ thì dừng, không retry mù. Sau lỗi connect trước mutation, kiểm marker bằng phép đọc hợp lệ trước khi gửi lại. Dừng ở 429 và giữ Retry-After, không né giới hạn. Fault không tồn tại trên API production. Multipart `fixtureFiles` giới hạn PDF/PNG/TXT định nghĩa sẵn; so sánh hash nguồn/tải, không đọc file cá nhân. `tls-info` và `authorizeShape` ghi metadata/boolean, không ghi URL authorize hay state/pollToken. Xem `architechture/docs/backend-supplemental-observations.md` cho kết quả G/J/L; không lặp bước đã đủ bằng chứng.

Hướng dẫn nghiệm thu còn thiếu và điều kiện cấp quyền: `architechture/docs/remaining-acceptance-guide.md`. Không retry, chuyển công cụ/agent hoặc nhờ chạy hộ thao tác credential đã bị safety gate chặn; không bỏ qua xác thực. Chỉ thực hiện nghiệp vụ trong buổi nghiệm thu hợp lệ riêng. Live test chỉ khi được cấp quyền Gmail chuyên test; không dùng OAuth mock giả lập evidence Google thật.

### Fault socket timeout và public config độc lập

- `-ManualStalls` yêu cầu `-ManualSession -ManualFaults`: chỉ fixture này đặt connect/read/write 2000 ms, 2 lần mở kết nối, wait 300 ms. `mail-fault` hỗ trợ `stall-imaps`, `stall-smtp`, `status-imaps`, `status-smtp` và `resume-imaps`/`resume-smtp`. Listener chỉ chấp nhận TCP loopback, không phản hồi greeting/TLS, không xử lý hay lưu credential. Status báo aggregate accepted/peerClosed/fixtureClosed/active; `fixtureClosed` không chứng minh peer tự đóng. Sau timeout mutation phải đọc lại trạng thái hợp lệ, không retry mù. T001–T013 đã kiểm 504/recovery, không cần chạy lại nếu không đổi code.
- `-VerifyDatabaseOnly -VerifyPublicConfigFault -SkipBuild -DatabaseEngine MySQL`: fixture mới, không kết hợp legacy/8443/manual. GET public qua CA PEM, tạm đổi tên bảng settings trong datadir được tạo và xác minh PID; restore trong finally. Đợi hết cache TTL, kiểm 500 JSON INTERNAL_ERROR đã lọc và GET 200 giống trước fault. Evidence `build/acceptance/public-config-fault-summary.txt`; không chạy trên DB hiện có. Đây không phải lỗi audit storage hoặc kiểm toàn bộ log privacy.

MySQL 9.5.0 native đã khởi động thành công trong datadir riêng, V1–V6, `utf8mb4_unicode_ci`, đủ 4 bảng nghiệp vụ, 9 setting và admin bootstrap; xem kết quả lần chạy cuối trong test matrix. Flyway cảnh báo 9.5 mới hơn phiên bản hỗ trợ chính thức của thư viện (8.1); không suy rộng thành nghiệm thu mọi phiên bản MySQL/volume triển khai. Với dữ liệu cũ trùng username/email không phân biệt hoa thường, xử lý xung đột có chủ đích trước khi áp dụng V5; migration sẽ không tự xóa/gộp user. Không chạy migration trên DB cá nhân/production chỉ để nghiệm thu; H2 ở chế độ MySQL chỉ là bằng chứng bổ sung.

### Quan sát audit storage / lease (fixture cô lập)

- `-ManualAuditFaults` chỉ dùng cùng ManualSession và native MySQL mới; control reject-audit/restore-audit/audit-status qua script fixture cố định, kiểm ownership PID, trigger chỉ reject INSERT audit_logs. Không nhận SQL tùy ý, không áp dụng trên DB hiện có. Dọn trigger trong finally.
- `-ManualLeases` bật IMAP pool riêng cho fixture (idle 10 s, max 1 idle store/key), lease-status chỉ đếm socket ESTABLISHED của PID Server tới hai listener loopback thuộc fixture. Không xem connection table thô.
- Privacy-status chỉ đếm marker synthetic định trước trong hai file log Server mới thuộc fixture, trả aggregate, không xuất raw log hoặc tìm credential. Evidence B020/B029; không chứng nhận toàn bộ log privacy.
- Khi audit bị reject, đổi settings rollback; nếu gửi/xóa/cập nhật cờ đã được mail server xác nhận thì đọc lại trạng thái, không gửi lại mù. Restore audit và kiểm thao tác mới ghi row. A/B đã xác minh; không lặp các bước đã đạt nếu không đổi code. A/B trước V7 chưa có outbox; V7 phục hồi sự kiện mới đã được lưu bền vững, không phục hồi lịch sử mất.
- Kết quả BE cuối ghép full 255 test với lần chạy lại hai suite sau chỉnh assertion privacy test-only. Nguồn `build/acceptance/be-full-before-privacy-assertion.json`, `be-final-suite-summary.json`; không coi XML targeted 21 test là toàn bộ BE. Kết quả Client/Newman riêng theo test matrix. Gmail chuyên test chưa cấp quyền; local rehearsal 7 MIME không thay live.

## V7 audit fault — fixture độc lập

Harness native MySQL manual `-ManualAuditFaults` dùng datadir/port mới, CA đúng fixture, không lấy dữ liệu ngoài root. Control hữu hạn reject/restore audit INSERT, intent INSERT, outcome UPDATE, drain DELETE; outbox-status chỉ trả counts, restart-server chỉ dừng/restart đúng PID server fixture, giữ datadir/GreenMail, recovery-privacy-status chỉ đếm marker synthetic. Không thao tác SQL tùy ý, không dump raw log hoặc secret. C001–041 đã chạy, không cần chạy lại nếu không có regression. Default replay 10 s (fixture 2 s); unresolved PREPARED/backlog quá 10 phút có cảnh báo. Không tự mutate mail để giải quyết PREPARED chưa rõ. C-UI chỉ standalone Compose không account/session, không endpoint; nếu không xác nhận focus thì dừng, không workaround. Live cần dedicated Gmail được phê duyệt, không nhờ ai chạy hộ gate. Chi tiết [residual guide](../../architechture/docs/remaining-acceptance-guide.md) và [crosswalk 61 OPEN](../../architechture/docs/open-criteria-current.md).

**Regression schema V7-L:** V7-L: Native MySQL database-only, legacy V1/V2 seed synthetic → V6 làm tiền đề → V7. Snapshot 4 bảng trước/sau V7 khớp; outbox mới rỗng, 7 migration success; khởi động JAR vẫn giữ nguyên dữ liệu, 9 settings/1 admin/5 bảng (4 cũ + outbox). Exit 0, datadir fixture đã xóa. Lý do regression riêng: V7 vừa thêm và legacy harness phải nhận version 7; không phải nghiệm thu lại các task migration/AES/TLS cũ. Client/Newman không chạy. Evidence Mail Server/build/acceptance/legacy-upgrade-summary-V7.txt và mysql-metadata-V7-legacy.tsv.

### Audit operational capacity và kiểm chứng D

`mail.audit.local-capacity` default 128/process (1–4096), `mail.audit.replay-delay-ms` mặc định 10000. Status ADMIN chỉ aggregate; stale PREPARED cần đối chiếu nghiệp vụ trong phiên được phép, không xóa row hoặc tự mark success/resend. Local acknowledgment chưa durable có thể mất khi process crash; durable intent vẫn ở DB. `-ManualAuditCapacity 2` chỉ dùng cùng `-ManualSession -ManualAuditFaults` trên native disposable MySQL để kiểm tra backpressure nhỏ, không thay auth hoặc production settings. D đã restore lỗi fixture và dọn root, không cần chạy lại nếu không có regression.

### V8 audit receipt và vận hành (vòng E)

V8 thêm cột lịch retry và correlation, giữ nguyên dữ liệu V7; không sửa migration đã phát hành. `MAILAPP_AUDIT_RECEIPT_DIR` / `mail.audit.receipt-dir` mặc định `./data/mail-audit-receipts`: persistent directory riêng mỗi instance, khóa owner file, đường dẫn real không symlink; POSIX 0700, Windows cần ACL riêng của service account. Không để directory/key dưới version control, không chia sẻ giữa process/DB/key khác nhau; backup/restore phải đồng bộ cùng database và AES key, không in receipt/key hoặc tự xóa receipt. Gradle tests được cấu hình receipt-dir rỗng riêng để cô lập unit tests; không tắt journal production. `mail.audit.retry-base-ms` mặc định 10000, exponential tối đa 300s, chỉ retry ghi audit; không retry mail. Receipt .pending hoàn chỉnh được phục hồi, file hỏng/conflict được giữ để điều tra và chặn nhận mới; kiểm status ADMIN và events chỉ metadata, không mark success. Force/atomic rename bảo vệ process restart đã thử, không phải bảo đảm mọi mất điện/filesystem/dual-store loss. PREPARED không có receipt vẫn chưa biết outcome. Chỉ dùng `-VerifyAuditUpgrade` và `-ManualAdminOverlap` cho disposable native harness đã được cấp quyền; flag overlap chỉ telemetry/trigger sacrificial user riêng, không production. E fixture đã dọn, không cần lặp nếu không regression. Không yêu cầu người dùng thực hiện thay thao tác đã bị chặn.

**Đóng gói V8:** Dockerfile tạo sẵn `/app/data/mail-audit-receipts` mode 0700 và owner mailapp; Compose mount named volume `mail_audit_receipts` đúng path. Giữ volume và AES key đồng bộ database khi recreate; không xóa volume khi còn unresolved. Chỉ kiểm cấu hình source/indent/mount; chưa build/run container hoặc thử ACL/volume trên Docker. Không đổi Java/JAR sau kiểm thử cuối.

## Vòng F — local provider faults và evidence

Opt-in fixture ManualFaults/ManualAuditFaults hỗ trợ deny-mail-auth/restore-mail-auth (existing synthetic mailbox, không thay credential), fail-after-delivery one-shot và provider-fault-status (aggregate only). OAuth fixture reject-refresh cấp grant ngắn qua authorize/callback/poll bình thường rồi từ chối refresh thực; refreshRejected là counter riêng. provider-error-privacy kiểm fixed synthetic marker log/outbox/audit, chỉ trả count, không raw log/private state. Chỉ fixture loopback owned PID, không production, không retry blocked auth hoặc chuyển công cụ. Không tự khởi chạy lại ca đã đủ F201–226.

MailAuditRecoveryIntegrationTest 13/13 (3 test mới): journal fail closed khi write/remove lỗi, kiểm lock/root và cả hai sibling receipt/pending, inventory không đầy đủ không được tin; giữ conflict/corrupt, không tự xóa. Recovered receipt phải validate lại trước finalize; unreserved finish không biến PREPARED thành success, observation đầu tiên không bị ghi đè; replay chỉ audit, không SMTP/IMAP mutation. Runtime fault test H2 giữ PREPARED khi DB reject, ghi đúng một audit từ observation còn RAM khi DB hồi phục, không duplicate và chỉ mở admission khi receipt xử lý xong. Không suy thành bảo đảm mọi OS crash/disk fault hay atomic MySQL–Gmail.

Dockerfile/Compose durable volume đã rà source E, chưa container runtime riêng F. Giữ dữ liệu receipt, không tự xóa conflicting sibling hoặc đổi outcome để mở admission. JAR cuối 117BC6063255AFB84A8FDC7E613070D458F4F05AE4C8875A5C2E2C5C2C7CCBC0; evidence vòng F trong build/acceptance, manual và integration root đã dọn.

## Vòng G2 — sau vòng F (kết quả hiện hành)

V9 là migration additive, revision BIGINT NOT NULL DEFAULT 0. Harness có -VerifyDatabaseOnly -VerifyAccountRevisionUpgrade chỉ dành cho MySQL loopback datadir mới độc lập, kiểm V8→V9 synthetic mailbox preservation và không đọc/in secret. Không sử dụng DB/volume hiện có. Các live suite vẫn gated dedicated TEST-ACCOUNT/opt-in; không bật hoặc chuyển thao tác bị safety gate chặn. Evidence: backend-round-G2-suite-summary.json, client-round-G2-suite-summary.json, postman-summary-round-G2.json, oauth-summary-round-G2.json, mysql-metadata-round-G2.tsv, account-revision-upgrade-summary-round-G2.txt (build/acceptance từng module). JAR SHA256 D5656D8FACAA0F4BF725237766471F1DFC8CDFD011567469568524715732D38D.

## Vòng G3 — kết quả hiện hành

Lũy kế Backend 279/284, Client 124/124, tổng 403 PASS / 408 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng G3 chạy mới BE 100/100, Client 66/66 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; 5 live SKIP kế thừa, không chạy Gmail thật hoặc full suite mới.

Evidence: build/acceptance/backend-round-G3-suite-summary.json, client-round-G3-suite-summary.json, postman-summary-round-G3.json, oauth-summary-round-G3.json, mysql-metadata-round-G3.tsv và jar-round-G3.sha256. JAR SHA256 02E672CD7CA5ED8B0A8987F9403BF28CB25EF290E941FA40E5E04B023A5EFC41.

V9 giữ nguyên, không chạy lại upgrade. Chỉ fixture loopback MySQL mới, H2/JPA, GreenMail và provider mock. Không dùng DB/volume/tài khoản thật, không lặp safety-gated thao tác. 293 PASS / 116 N/A / 59 OPEN, 26 task đóng đầy đủ, 52 task còn OPEN; 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Không có tiêu chí mới đủ toàn bộ nghĩa vụ. Không lặp thao tác credential bị gate, C/D2/E1, AM-4309, AM-4343, E001, E042; không thay bootstrap.

## Vòng G4 — kết quả hiện hành

Lũy kế Backend 280/285, Client 126/126, tổng 406 PASS / 411 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng G4 chạy mới BE 34/34, Client 46/46 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; 5 live SKIP kế thừa, không chạy Gmail thật hoặc full suite mới.

G4 R01–R12: observer CLI tương tác từng lệnh trên factory thật từ JAR G4, GreenMail mới loopback; account/password/AES synthetic ngẫu nhiên chỉ trong process, không app login/DB. Raw Store/Transport chưa connect khi cấp; caller connect/close, INBOX READ_ONLY total=2/unread=2, Folder close không đóng Store; FolderNotFoundException và caller abort trước send đều đóng scope. Final storeOpen=false/transportOpen=false, fixtureMessages=2, quit exit0, root removed. Không SMTP DATA fault mới, không suy isConnected thành đo mọi socket OS; không HTTP/Postman/native Swing/Gmail live mới.

G4: RealGmailIntegrationTest bổ sung raw standalone Store.connect/READ_ONLY/count/close và Transport connect/close trong nhánh App Password opt-in hiện hữu, dùng account/AES chỉ trong memory. RealGmailMimeFixturesTest bổ sung metadata sample ordinal, INBOX UID/UIDVALIDITY/attachment count sau verify exact bytes; không log raw content/secret, giữ mẫu cho manual, không tự xóa hoặc retry khi kết quả chưa rõ. Hai source đã compile, chưa chạy live; vẫn cần dedicated account/consent/permission và verify targeted cleanup/response-log. Không thay consent hoặc token flow.

Evidence module build/acceptance: backend-round-G4-suite-summary.json, client-round-G4-suite-summary.json, postman-summary-round-G4.json, oauth-summary-round-G4.json, mysql-metadata-round-G4.tsv, manual-raw-round-G4.tsv và jar-round-G4.sha256. JAR SHA256 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A.

V9 giữ nguyên, không upgrade lại; hai fixture G4 đã dọn.

## G5 — 26/11/2026

Lũy kế Backend 280/285, Client 128/128, tổng 408 PASS / 413 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G5 chạy mới Client 49/49 (gồm E2E 16/16); Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion là kết quả G4 kế thừa, không chạy lại. Không Gmail thật, native manual, HTTP manual hoặc full suite mới.

78 task / 468 tiêu chí: 293 PASS, 116 N/A, 59 OPEN; 26 task đóng, 52 task còn OPEN. Ma trận hiện có 171 nhóm: 80 có K/M trong scope, 91 còn điều kiện. Không đổi checkbox; G5 không đóng thêm toàn criterion.

Evidence G5: Mail Client/build/acceptance/client-round-G5-suite-summary.json (4 suite; 12 AcceptanceRegressionTest, 16 ClientLiveServerE2ETest, 5 GmailMultiAuthClientTest, 16 SwingFeatureAcceptanceTest; 49/49, 0 skip/fail/error); Mail Server/build/acceptance/mysql-metadata-round-G5.tsv (MySQL native 9.5.0, utf8mb4_unicode_ci, V1–V9, 9 settings, 1 admin, 5 bảng). Runner -SkipBuild -DatabaseEngine MySQL -ClientTests với 4 suite trên, không -RunPostman; BUILD SUCCESSFUL, fixture đã dọn. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A. Newman G4 140/653, OAuth loopback G4 và BE XML G4 chỉ kế thừa.

Điều kiện H/N/L/I/D còn lại theo từng hàng của ma trận và residual: phiên nghiệp vụ được cấp quyền đúng thao tác, native desktop thực sự thuộc fixture và nhận input, dedicated Gmail consent/môi trường live, exact-two-admin fixture hợp lệ không sửa bootstrap, và owner duyệt contract. Không có điều kiện mới trong G5. Không coi E2E là native/manual; không coi GreenMail là Gmail thật. Chưa có bằng chứng đóng thêm criterion.

## G6 — 26/11/2026

78 task / 468 tiêu chí: 297 PASS, 116 N/A, 55 OPEN; 29 task đóng, 49 task còn OPEN. 297/352 tiêu chí áp dụng = 84,375%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Giữ 171 ID: 81 K/M có evidence, 86 điều kiện trực tiếp thiếu và 4 tham chiếu S chưa đạt; tổng 90 hàng chưa có đủ evidence.

G6 đóng đúng bốn checkbox Manual test pass local AM-4320, AM-4323, AM-4358, AM-4359 từ evidence quan sát đã có, đối chiếu §4 và phân công 02/03; không thêm manual run. AM-4320/4358/4359 đóng task, AM-4323 còn contract D/L. Đây là điều chỉnh phạm vi nghiệm thu, không thay yêu cầu gốc hay đánh dấu live PASS. Bốn hàng S giữ nguyên ID/điều kiện gốc và link đến criterion vẫn OPEN; không tính S là K/M hay N/A. Nếu yêu cầu sign-off đúng công cụ/human ngoài local agent thì vẫn phải xác minh riêng.

Evidence G6: Mail Client/build/acceptance/client-round-G6-suite-summary.json; 3 suite: GmailMultiAuthClientTest 5/5, MailboxRegressionTest 13/13, SwingFeatureAcceptanceTest 16/16, tổng 34/34, 0 skip/fail/error. gradlew.bat test --tests *MailboxRegressionTest --tests *SwingFeatureAcceptanceTest --tests *GmailMultiAuthClientTest --max-workers=1 --no-daemon --console=plain --offline, BUILD SUCCESSFUL. Hai test mới; không cộng 32 test rerun vào lũy kế. E2E/MySQL G5, Newman/OAuth loopback/BE G4 kế thừa; không tạo fixture server mới. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A.

H/I/N/L/D còn thiếu theo từng hàng ma trận: phiên nghiệp vụ hợp lệ được cấp quyền độc lập, fixture exact-two-admin hợp lệ không sửa bootstrap, desktop native đúng PID/focus/input, dedicated Gmail consent/scope và kết nối thật, owner phê duyệt contract. Không coi overlap 3 admin là exact-two; không coi E2E là native/manual hay GreenMail là Gmail. Không phát hiện phần triển khai độc lập cần sửa thêm trong scope đã rà; không kết luận mọi lỗi đều hết. Không thể đạt 100% khi điều kiện này chưa thỏa.

## Phê duyệt contract — 26/11/2026 (sau G6)

78 task / 468 tiêu chí: 298 PASS, 116 N/A, 54 OPEN; 30 task đóng, 48 task còn OPEN. 298/352 tiêu chí áp dụng = 84,659%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 5 contract, 3 privacy/live. 171 ID: 81 K/M kế thừa + 3 A phê duyệt = 84 nghĩa vụ đã thỏa; 83 trực tiếp thiếu + 4 shared S = 87 còn thiếu.

Chủ dự án phê duyệt 26/11/2026, sau G6: AM-4323-C.03 A — SEND_MAIL metadata-only (masked sender/counts, không raw/hash To/subject/body), C.04 vẫn L cả hai mode Gmail, delivery/read-back/attachments, shared 4323-M.05. AM-4340-C.03 A — bootstrap từ ADMIN_INITIAL_PASSWORD/cấu hình, BCrypt và forced-change, không log plaintext, không ghi đè Admin, Flyway V1–V9 additive; với evidence MySQL/seed kế thừa, contract và task đã PASS. AM-4391-C.02 A — explicit permanent=true trong Trash, UIDVALIDITY, targeted UID MOVE/UIDPLUS, default 204, Prefer 200 tùy chọn, fail-closed; C.03 vẫn L Gmail Trash/All Mail thật. Không có D còn chờ; phê duyệt không thay bằng chứng live, human/manual, test hay triển khai.

Không chạy test mới, không thay source/JAR, giữ JUnit 410/415 (5 live SKIP), Newman 653/653 kế thừa và E2E 16/16 kế thừa. Mọi mô tả D/chờ phê duyệt ở các mốc trước là lịch sử, không phải residual hiện hành.
