# Hướng dẫn tích hợp MailConnectionFactory (M07)

> **Tác giả:** Lê Thanh Cường (Module M07 & M06)  
> **Áp dụng cho:** Toàn bộ thành viên nhóm (TV2 — Thành, TV4 — Khánh, TV5 — Thọ, TV6 — Đức)  
> **Package:** `com.mailapp.mail`  

---

## 1. Tổng quan & Nguyên tắc cốt lõi

`MailConnectionFactory` là **bộ điều phối kết nối duy nhất** đến Google Gmail (IMAP over TLS và SMTP STARTTLS). Mọi module đọc mail, gửi mail, xóa mail hay kiểm tra thông tin liên kết **bắt buộc** gọi qua Factory này, không tự ý khởi tạo `Session`, `Store` hay `Transport` riêng lẻ.

### Các nguyên tắc thiết kế:
1. **Không cache session toàn cục:** Mỗi lần mở kết nối là một `Session` độc lập với `debug=false`, tránh rò rỉ credential giữa các người dùng.
2. **AutoCloseable an toàn:** `ImapConnection` và `SmtpConnection` triển khai `AutoCloseable`. Phương thức `close()` nuốt sạch ngoại lệ để đảm bảo an toàn tuyệt đối khi dùng trong khối `try-with-resources`.
3. **Bảo mật thông tin xác thực:** `MailCredentials.toString()` luôn che mật khẩu và token (`secret=***`, `c***@gmail.com`). Log hệ thống tuyệt đối không in secret.
4. **Tự động phân luồng xác thực:** Factory tự động phát hiện `AuthType.APP_PASSWORD` hoặc `AuthType.OAUTH2` (SASL `XOAUTH2`) dựa trên entity `GmailAccount`.

**Tương thích Java API AM-4358/4359/4360:** `createImapStore(account)`, `createSmtpTransport(account)` trả raw Store/Transport **chưa kết nối, không thuộc pool**; caller đã xác minh ownership phải dùng try-with-resources và tự gọi `connect()`. `createSession(account, MailConnectionFactory.Protocol.IMAP/SMTP)` trả Session độc lập sử dụng cùng cấu hình TLS, timeout, XOAUTH2 và Authenticator giới hạn theo server/protocol/port/user. Không lưu password/token trong Properties. API thấp này chuẩn bị credential theo preferred method, không tuyên bố login thành công trước handshake, không tự retry hoặc thực thi fallback sau khi caller gọi connect. Nghiệp vụ vẫn phải dùng `openImap/openSmtp` để có phân loại lỗi, lifecycle, retry kết nối và multi-auth fallback đầy đủ; không lấy Session/credential qua HTTP.

---

## 2. Sơ đồ luồng mở kết nối (Mermaid)

```mermaid
sequenceDiagram
    autonumber
    actor Caller as Teammate Service (M04/M08/M10/M16)
    participant Factory as DefaultMailConnectionFactory
    participant Retry as Resilience4j Retry
    participant Provider as AccessTokenProvider
    participant Gmail as Google Mail Server (IMAPS/SMTP)

    Caller->>Factory: openImap(account) / openSmtp(account)
    Factory->>Factory: Phân tích AuthType (APP_PASSWORD / OAUTH2)
    alt Là OAuth2
        Factory->>Provider: getValidAccessToken(account)
        opt Token hết hạn hoặc < 60s
            Provider->>Gmail: POST /token (refresh_token)
            Gmail-->>Provider: access_token mới
        end
        Provider-->>Factory: accessToken
    else Là App Password
        Factory->>Factory: AesCipher.decrypt(appPasswordEncrypted)
    end
    Factory->>Retry: Thực thi kết nối (kèm Retry lỗi mạng)
    Retry->>Gmail: Mở kết nối TLS (Port 993 / 587)
    alt Xác thực thất bại
        Gmail-->>Factory: AuthenticationFailedException
        Factory-->>Caller: Ném MailAuthException (HTTP 422 - KHÔNG RETRY)
    else Token bị thu hồi (invalid_grant)
        Factory-->>Caller: Ném ReauthRequiredException (HTTP 422 - KHÔNG RETRY)
    else Lỗi mạng tạm thời / Timeout
        Gmail-->>Retry: SocketTimeoutException / ConnectException
        Retry->>Gmail: Thử lại (tối đa 2 lần, backoff 300ms)
    else Thành công
        Gmail-->>Factory: Connected Store / Transport
        Factory-->>Caller: ImapConnection / SmtpConnection (AutoCloseable)
    end
```

---

## 3. Hướng dẫn tích hợp cho từng Module

### 3.1. Cao Tất Thành (TV2 — M04: Thêm & Kiểm tra Gmail App Password)
Khi người dùng nhập form email + App Password 16 ký tự, gọi `testImap` để kiểm tra đăng nhập thật với Gmail trước khi lưu vào DB. Hàm này **không bao giờ ném exception cho lỗi dự kiến**:

```java
@Autowired
private MailConnectionFactory mailConnectionFactory;

public void linkGmailWithAppPassword(Long userId, String email, String rawAppPassword) {
    // 1. Thử đăng nhập IMAP thật với Gmail
    ConnectionTestResult testResult = mailConnectionFactory.testImap(email, rawAppPassword);
    
    if (!testResult.isSuccessful()) {
        switch (testResult.status()) {
            case AUTH_FAILED -> throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "GMAIL_AUTH_FAILED", testResult.message());
            case IMAP_DISABLED -> throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "IMAP_DISABLED", testResult.message());
            case NETWORK_ERROR -> throw new AppException(HttpStatus.BAD_GATEWAY, "GMAIL_CONNECTION_ERROR", testResult.message());
        }
    }

    // 2. Nếu thành công -> Mã hóa và lưu DB
    String encryptedPassword = aesCipher.encrypt(rawAppPassword);
    // ... lưu vào bảng gmail_accounts
}
```

---

### 3.2. Trần Nguyễn Hữu Khánh (TV4 — M08: Đọc mail IMAP)
Khi fetch danh sách thư mục hoặc tải danh sách mail:

```java
@Autowired
private MailConnectionFactory mailConnectionFactory;

@Autowired
private GmailFolderResolver folderResolver;

public List<MailSummaryDto> fetchInboxMails(GmailAccount account, int page, int size) {
    // Luôn sử dụng try-with-resources để tự động đóng kết nối IMAP
    try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
        Folder inbox = imap.folder("INBOX");
        inbox.open(Folder.READ_ONLY);

        // Fetch danh sách mail theo trang
        // ...
        inbox.close(false);
        return mailList;
    } catch (MessagingException e) {
        throw new MailNetworkException("Không thể đọc hộp thư đến", e);
    }
}

// Phân giải thư mục theo chuẩn đa ngôn ngữ (không hardcode tiếng Anh):
public Folder getSentFolder(ImapConnection imap) throws MessagingException {
    return folderResolver.resolve(imap.getStore(), GmailFolderResolver.SpecialFolder.SENT);
}
```

---

### 3.3. Nguyễn Tá Thọ (TV5 — M10: Soạn & Gửi mail qua SMTP)
Khi gửi mail:

```java
@Autowired
private MailConnectionFactory mailConnectionFactory;

public void sendMail(GmailAccount account, SendMailRequest request) {
    // Mở kết nối SMTP an toàn
    try (SmtpConnection smtp = mailConnectionFactory.openSmtp(account)) {
        MimeMessage message = smtp.createMimeMessage();
        
        message.setFrom(new InternetAddress(account.getEmail()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(request.to()));
        message.setSubject(request.subject(), "UTF-8");
        
        // Dựng body text/html và đính kèm...
        message.setText(request.body(), "UTF-8");
        
        // Gửi qua Transport đã xác thực
        smtp.sendMessage(message);
    } catch (MessagingException e) {
        throw new MailNetworkException("Gửi thư thất bại qua SMTP", e);
    }
}
```

---

### 3.4. Trần Minh Đức (TV6 — M16: Xóa mail theo cơ chế Trash)
Khi người dùng xóa mail, di chuyển vào thùng rác trên Gmail:

```java
@Autowired
private MailConnectionFactory mailConnectionFactory;

@Autowired
private GmailFolderResolver folderResolver;

public void moveMailToTrash(GmailAccount account, String sourceFolderName, long uid) {
    // Không triển khai expunge trực tiếp: dùng MailService.deleteMail với
    // currentUser, gmailId, folder, uid, permanent=false, uidValidity, clientIp.
    // MailService kiểm tra ownership và UIDVALIDITY, ưu tiên UID MOVE nếu có.
    // Nếu không có MOVE thì cần UIDPLUS để expunge đúng UID; xóa vĩnh viễn
    // luôn cần UIDPLUS. Không retry mutation khi kết quả chưa xác định.
}
```

---

## 4. Bảng mã lỗi & Ánh xạ HTTP Status

| Ngoại lệ | HTTP Status | Error Code | Nguyên nhân | Hành động gợi ý cho Client |
|---|:---:|---|---|---|
| `MailAuthException` | `422` | `GMAIL_AUTH_FAILED` | Sai App Password hoặc token bị từ chối | Yêu cầu nhập lại App Password chính xác |
| `ReauthRequiredException` | `422` | `GMAIL_NEEDS_REAUTH` | OAuth2 refresh token bị Google thu hồi | Mở lại luồng OAuth2 để liên kết lại |
| `ImapDisabledException` | `422` | `IMAP_DISABLED` | Chưa bật IMAP trong Settings của Gmail | Hướng dẫn bật IMAP trên web Gmail |
| `MailNetworkException` | `502` / `504` | `GMAIL_CONNECTION_ERROR` | Mất mạng, timeout, lỗi TLS handshake | Thông báo kiểm tra đường truyền và thử lại |

---

## 5. Cấu hình tham số (`application.properties`)

```properties
# Bật Virtual Threads Java 21 (IMAP / SMTP socket I/O là blocking I/O)
spring.threads.virtual.enabled=true

# Cấu hình kết nối Gmail
mail.gmail.imap.host=imap.gmail.com
mail.gmail.imap.port=993
mail.gmail.smtp.host=smtp.gmail.com
mail.gmail.smtp.port=587

# Timeouts (đơn vị: mili-giây)
mail.gmail.connection-timeout-ms=10000
mail.gmail.timeout-ms=15000
mail.gmail.write-timeout-ms=30000

# Cấu hình Resilience4j Retry lỗi mạng
mail.gmail.retry.enabled=true
mail.gmail.retry.max-attempts=2
mail.gmail.retry.wait-duration-ms=300
```

Probe kiểm tra credential (`testImap`) luôn mở kết nối mới, không dùng pool, không retry mạng, giới hạn từng timeout socket connect/read/write tối đa 10 giây và đóng vật lý khi kết thúc. Đây không phải cam kết deadline tổng cho DNS + TLS + IMAP. Kết nối thông thường giữ retry và pooling. Không dùng wildcard trust; `mail.gmail.local-test-transport=true` chỉ dành cho fixture GreenMail loopback dùng credential giả; mặc định false, IMAPS xác minh hostname/truststore và SMTP bắt buộc STARTTLS. Không áp dụng ngoại lệ này cho `host.docker.internal`.

> **Tại sao cần Virtual Threads của Java 21?**  
> Các tác vụ IMAP và SMTP qua Jakarta Mail là thao tác Blocking Socket I/O. Khi người dùng fetch mail hoặc gửi mail dung lượng lớn, luồng xử lý sẽ bị block chờ mạng. Sử dụng Virtual Threads giúp Spring Boot phục vụ hàng ngàn kết nối đồng thời mà không làm cạn kiệt pool luồng nền tảng (OS threads) của máy chủ.

---

## 6. Hướng dẫn thiết lập Gmail cho kiểm thử

### 6.1. Bật IMAP trên Gmail
1. Truy cập [Gmail Web](https://mail.google.com/) $\rightarrow$ Bấm icon bánh răng **Cài đặt** $\rightarrow$ **Xem tất cả chế độ cài đặt**.
2. Chọn tab **Chuyển tiếp và POP/IMAP**.
3. Tại mục **Quyền truy cập qua IMAP**, chọn **Bật IMAP**.
4. Bấm **Lưu thay đổi**.

### 6.2. Tạo App Password (Mật khẩu ứng dụng 16 ký tự)
1. Đảm bảo tài khoản Gmail đã bật **Xác minh 2 bước (2-Step Verification)**.
2. Truy cập [Quản lý tài khoản Google](https://myaccount.google.com/security).
3. Tìm kiếm **Mật khẩu ứng dụng (App passwords)**.
4. Đặt tên ứng dụng (ví dụ: `MailApp PTIT`) và bấm **Tạo**.
5. Google sẽ cấp một mật khẩu gồm 16 ký tự (định dạng placeholder: `<GMAIL_APP_PASSWORD_16_KY_TU>`). Lưu lại chuỗi này để sử dụng trên Mail App.

### 6.3. Tích hợp XOAUTH2 thật (Nối Module M06 và M07)
1. **Điều phối Access Token**: `DefaultAccessTokenProvider` kết nối trực tiếp với `OAuth2Service.getFreshAccessToken(account)`.
2. **Cơ chế Caching & Lock**: Access Token được lưu trong bộ nhớ RAM qua **Caffeine Cache** (hết hạn trước 60 giây so với Google), tuyệt đối **không lưu Access Token vào DB**. Mỗi tài khoản được bảo vệ bằng khóa đồng bộ (`accountLocks`) để tránh race condition khi nhiều luồng cùng gọi làm mới token.
3. **Cơ chế SASL XOAUTH2**: Cả IMAP và SMTP đều được cấu hình cơ chế SASL chuẩn:
   - `mail.imaps.auth.mechanisms=XOAUTH2` & `mail.imaps.sasl.enable=true`
   - `mail.smtp.auth.mechanisms=XOAUTH2` & `mail.smtp.sasl.enable=true`
   - Tắt các cơ chế xác thực thuần không an toàn: `auth.login.disable=true`, `auth.plain.disable=true`.
4. **Xử lý thu hồi quyền (Invalid Grant)**: Khi Google trả về lỗi `invalid_grant` (người dùng đổi mật khẩu hoặc hủy quyền trên trang bảo mật Google), hệ thống tự động cập nhật `status = NEEDS_REAUTH`, lưu DB, ghi Audit Log và ném `ReauthRequiredException` để Client nhắc người dùng liên kết lại.

### 6.4. Chạy kiểm thử tích hợp với Gmail thật
Chỉ dùng hộp thư Gmail test riêng, xác nhận TEST-ACCOUNT. Runner App Password nhập mật khẩu ẩn bằng `Read-Host -AsSecureString`, truyền vào tiến trình con, không ghi vào file. Suite kiểm cả IMAPS/SMTP và vòng đời thư tự gửi có định danh riêng:
```powershell
cd "Mail Server"
.\scripts\test-real-gmail.ps1
# Neu can giu 7 thu mau MIME de kiem tra tay:
.\scripts\test-real-gmail.ps1 -PrepareMimeFixtures
```
Với XOAUTH2, consent/profile/revoke và cách chuẩn bị fixture, xem mục Gmail test thật trong [dev-setup.md](dev-setup.md). Test OAuth bằng access token ngắn hạn không chứng minh quy trình consent/refresh/revoke hoàn chỉnh. Chưa chạy Gmail thật nếu thiếu tài khoản riêng và credential opt-in.

### 6.5. Vòng đời lease và pool

`mail.gmail.pool.enabled` áp dụng theo cấu hình, mặc định true (180 s idle, max 2 idle Stores cho mỗi key mailbox/method/credential fingerprint). Giới hạn này không phải giới hạn số kết nối đang được mượn đồng thời. Maintenance thread dùng monotonic time, quét mỗi giây, loại entry hết hạn và empty key kể cả khi không có borrow mới; đóng pool khi shutdown. Các close idempotent; wrapper đã trả lease không được tiếp tục dùng. Lease lỗi protocol/folder close bị loại khỏi pool; store/transport mở lỗi hoặc lưu trạng thái thất bại được đóng. Raw Store/Transport từ API tương thích vẫn là tài nguyên caller-owned, không thuộc pool.

Regression GreenMail kiểm reuse cùng Store, broken close, idle expire và late close sau shutdown, capacity và mailbox isolation. B002–006/B030–037 trên JAR cuối với pool fixture explicit idle10/max1 quan sát IMAPS1→0 khi idle, warm disconnect 502/0 socket rồi resume200/1 socket, sau idle lại0; SMTP0 sau send. Không suy count thành mọi remote socket/lifecycle đã được kiểm hoặc Gmail TLS live.

Cùng bộ 7 mẫu MIME dự kiến Gmail live được rehearsal tự động local qua `DefaultMailConnectionFactoryGreenMailTest#allSevenDedicatedMimeSamplesRehearseThroughLocalSmtpAndImap` (builder chung với RealGmailMimeFixturesTest, kiểm metadata/attachment bytes). Chỉ purge mailbox GreenMail do suite sở hữu; không expunge Gmail thật. Năm test live vẫn SKIP, không đổi điều kiện opt-in.

### 6.6. Quan sát provider fault local vòng F

F204/F205: actual provider rejection trên existing linked App Password mailbox, HTTP422 GMAIL_AUTH_FAILED; F207/F217 sockets0; F209 folders200 sau restore. F222: local OAuth refresh invalid_grant -> 422 GMAIL_NEEDS_REAUTH, account NEEDS_REAUTH. F213–216 SMTP delivery đã xảy ra nhưng ACK lỗi, 502 unknown outcome, đúng một thư/uncertain audit; không replay mutation. Đây là wrapper/service behavior trên GreenMail/OAuth fixture, không chứng minh raw caller-owned Store/Transport lifecycle, successful refresh/fallback hay real Gmail remote TLS/hostname/XOAUTH2. Các giới hạn còn mở ở residual index.

## Vòng G2 — sau vòng F (kết quả hiện hành)

V9 native V8→V9 preservation: owner, dual methods, trạng thái và ciphertext synthetic giữ nguyên, revision=0; fresh V1–V9. JPA optimistic version chống stale write/delete; 409 GMAIL_ACCOUNT_CHANGED an toàn. OAuth cache ràng buộc grant/owner, publish sau save; reject scope giảm/invalid_grant chính xác, tránh refresh lặp, persist rotation; fallback chỉ active sau connect, failure không audit thành công, scoped SMTP authenticator, resource close khi save conflict. Local raw lifecycle và factory regression PASS, không chứng minh Gmail live, mọi race hoặc atomic revoke/DB. Audit journal V8 kế thừa không sửa/replay mutation.

Raw create* trả object chưa connect, caller chịu trách nhiệm close; wrapper open* giữ retry an toàn trước mutation và close/lease theo trạng thái. Authenticator giới hạn host/protocol/port/user đã cấu hình; không thay TLS verification. Local automated raw lifecycle không thay standalone Gmail live.

## Vòng G3 — kết quả hiện hành

G3: MailboxRevisionChainIntegrationTest 2/2 trên H2/JPA thật (OSIV=false) và provider loopback: rotate refresh token commit, snapshot detached nhận đúng revision từ chính save; stale grant/cache bị từ chối trước provider; invalid_grant→NEEDS_REAUTH→fallback thực qua GreenMail có hai revision liên tiếp, đúng một audit mỗi action (mock audit), Store đóng; method bị gỡ thì snapshot cũ không mở raw/wrapper. Factory kiểm owner/id/revision trước credential/cache và sau open, đóng resource/lease nếu conflict. Không thay V9/schema, không retry mutation. Guard không bảo đảm thu hồi kết nối đã cấp hoặc atomic MySQL–Gmail.

Raw adapter vẫn caller-owned: không thể kiểm tra lại ở lúc caller tự connect sau khi đã nhận raw object. Wrapper kiểm trước/sau open; không thay TLS, không đảm bảo remote revocation atomic.

## Vòng G4 — kết quả hiện hành

G4 R01–R12: observer CLI tương tác từng lệnh trên factory thật từ JAR G4, GreenMail mới loopback; account/password/AES synthetic ngẫu nhiên chỉ trong process, không app login/DB. Raw Store/Transport chưa connect khi cấp; caller connect/close, INBOX READ_ONLY total=2/unread=2, Folder close không đóng Store; FolderNotFoundException và caller abort trước send đều đóng scope. Final storeOpen=false/transportOpen=false, fixtureMessages=2, quit exit0, root removed. Không SMTP DATA fault mới, không suy isConnected thành đo mọi socket OS; không HTTP/Postman/native Swing/Gmail live mới.

G4: RealGmailIntegrationTest bổ sung raw standalone Store.connect/READ_ONLY/count/close và Transport connect/close trong nhánh App Password opt-in hiện hữu, dùng account/AES chỉ trong memory. RealGmailMimeFixturesTest bổ sung metadata sample ordinal, INBOX UID/UIDVALIDITY/attachment count sau verify exact bytes; không log raw content/secret, giữ mẫu cho manual, không tự xóa hoặc retry khi kết quả chưa rõ. Hai source đã compile, chưa chạy live; vẫn cần dedicated account/consent/permission và verify targeted cleanup/response-log. Không thay consent hoặc token flow.

## G5 — 26/11/2026

G5 không đổi factory/source live hoặc chạy lại R01–R12/Gmail; G4 raw local và các evidence fixture giữ nguyên. Dedicated Gmail account, consent, permission và test thật vẫn là điều kiện L; không thay bằng mock.

Evidence G5: Mail Client/build/acceptance/client-round-G5-suite-summary.json (4 suite; 12 AcceptanceRegressionTest, 16 ClientLiveServerE2ETest, 5 GmailMultiAuthClientTest, 16 SwingFeatureAcceptanceTest; 49/49, 0 skip/fail/error); Mail Server/build/acceptance/mysql-metadata-round-G5.tsv (MySQL native 9.5.0, utf8mb4_unicode_ci, V1–V9, 9 settings, 1 admin, 5 bảng). Runner -SkipBuild -DatabaseEngine MySQL -ClientTests với 4 suite trên, không -RunPostman; BUILD SUCCESSFUL, fixture đã dọn. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A. Newman G4 140/653, OAuth loopback G4 và BE XML G4 chỉ kế thừa.

## G6 — 26/11/2026

PASS manual local scope interface: 03 M07 tách Interface MailConnectionFactory, implementation App Password, XOAUTH2 và Test factory kết nối thật; 02 C3 chốt interface sớm. G4 R02–R10 actual raw Store/Transport caller-owned connect/close và lỗi scope; H/B/T/F wrapper HTTP/lease/timeout; config có code/test. CLI không gọi là Postman. 4358-M.04 S/L remote hostname/TLS/timeout và hai auth mode vẫn phải thỏa AM-4361-M.02/C.02.

PASS manual local scope implementation App Password theo 03 M07: G4 R02–R10 raw AES synthetic, IMAPS/SMTP caller-owned; H/F/G/T connect/close/error/recovery thực local. F204/F205 deny trên link đã có, không phải POST wrong App Password. 4359-M.03 S/H/L vẫn phải thỏa AM-4314-M.03/M.04 (POST link/probe), AM-4396-M.02/M.03 (dedicated 2FA/standalone) và AM-4361-M.02/C.02 (remote TLS/timeout). Không claim Gmail thật.

Giữ shared 4320-M.05, 4358-M.04, 4359-M.03: remote hostname/TLS/timeout cả hai auth mode, special-use/count, delivery/read-back/hash/targeted cleanup. Manual/contract/privacy live vẫn OPEN.

Không thay Backend/JAR, bootstrap, schema, API contract, permission, JWT/RBAC/PKCE/TLS/UIDVALIDITY/audit/recovery. Guard Client không thu hồi request đã dispatch hoặc consent remote. Không retry hành vi credential bị chặn, không đổi công cụ, worker, fake session/token, private state hoặc yêu cầu người khác làm thay. Native desktop vẫn thiếu điều kiện xác nhận; không chạy lại C/D2/E1. Không có live/human sign-off mới.
